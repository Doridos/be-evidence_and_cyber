package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.DeviceSnapshot;
import cz.fel.cvut.beevidence_and_cyber.dao.RetentionSettings;
import cz.fel.cvut.beevidence_and_cyber.dto.PurgeResultDto;
import cz.fel.cvut.beevidence_and_cyber.dto.RetentionSettingsDto;
import cz.fel.cvut.beevidence_and_cyber.dto.SystemCapacityDto;
import cz.fel.cvut.beevidence_and_cyber.dto.TableSizeEntryDto;
import cz.fel.cvut.beevidence_and_cyber.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataRetentionService {

    @PersistenceContext
    private EntityManager entityManager;

    private final RetentionSettingsRepository retentionSettingsRepository;
    private final DeviceLogEntryRepository deviceLogEntryRepository;
    private final FileSystemEventRepository fileSystemEventRepository;
    private final TelemetrySampleRepository telemetrySampleRepository;
    private final AgentHeartbeatRepository agentHeartbeatRepository;
    private final DeviceSnapshotRepository deviceSnapshotRepository;
    private final NetworkInterfaceRepository networkInterfaceRepository;
    private final LoggedInSessionRepository loggedInSessionRepository;
    private final DetectionFindingRepository detectionFindingRepository;
    private final DetectionFindingEventRepository detectionFindingEventRepository;
    private final RemoteSessionRepository remoteSessionRepository;
    private final AIAnalysisRunRepository aiAnalysisRunRepository;

    // ─── Settings ────────────────────────────────────────────────────────────

    public RetentionSettings getSettings() {
        return retentionSettingsRepository.findById(1).orElseGet(() -> {
            RetentionSettings defaults = new RetentionSettings();
            return retentionSettingsRepository.save(defaults);
        });
    }

    @Transactional
    public RetentionSettings updateSettings(RetentionSettingsDto dto) {
        RetentionSettings settings = getSettings();
        settings.setRetentionDays(dto.retentionDays());
        settings.setMaxDbSizeGb(dto.maxDbSizeGb());
        settings.setRetentionEnabled(dto.retentionEnabled());
        return retentionSettingsRepository.save(settings);
    }

    // ─── Capacity ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public SystemCapacityDto getSystemCapacity() {
        RetentionSettings settings = getSettings();

        // Total DB size (PostgreSQL-specific)
        Object rawSize = entityManager
                .createNativeQuery("SELECT pg_database_size(current_database())")
                .getSingleResult();
        long dbSizeBytes = toLong(rawSize);

        // Per-table sizes (public schema only)
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT tablename,
                       pg_total_relation_size(quote_ident(tablename))
                FROM pg_tables
                WHERE schemaname = 'public'
                ORDER BY 2 DESC
                """).getResultList();

        List<TableSizeEntryDto> tables = rows.stream()
                .map(row -> new TableSizeEntryDto(
                        String.valueOf(row[0]),
                        toLong(row[1])
                ))
                .toList();

        long maxBytes = (long) settings.getMaxDbSizeGb() * 1024 * 1024 * 1024;
        double usagePct = maxBytes > 0 ? Math.min(100.0, dbSizeBytes * 100.0 / maxBytes) : 0.0;

        return new SystemCapacityDto(
                dbSizeBytes,
                maxBytes,
                Math.round(usagePct * 10.0) / 10.0,
                tables,
                settings.getRetentionDays(),
                settings.getMaxDbSizeGb(),
                settings.isRetentionEnabled(),
                settings.getLastPurgedAt()
        );
    }

    // ─── Purge ────────────────────────────────────────────────────────────────

    /**
     * Runs the full retention purge: time-based first, then size-based if still over limit.
     * Called by the scheduler and available for manual trigger via REST.
     */
    @Transactional
    public PurgeResultDto runPurge() {
        RetentionSettings settings = getSettings();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(settings.getRetentionDays());
        log.info("Starting retention purge — cutoff: {}, maxDbSizeGb: {}", cutoff, settings.getMaxDbSizeGb());

        Map<String, Long> counts = new LinkedHashMap<>();

        // 1. Time-based purge across all telemetry / log / event tables
        counts.put("logs", purgeOlderThan("logs", cutoff));
        counts.put("fileSystemEvents", purgeOlderThan("fileSystemEvents", cutoff));
        counts.put("telemetry", purgeOlderThan("telemetry", cutoff));
        counts.put("heartbeats", purgeOlderThan("heartbeats", cutoff));
        counts.put("snapshots", purgeOlderThan("snapshots", cutoff));
        counts.put("detectionFindings", purgeOlderThan("detectionFindings", cutoff));
        counts.put("remoteSessions", purgeOlderThan("remoteSessions", cutoff));
        counts.put("aiRuns", purgeOlderThan("aiRuns", cutoff));

        // 2. Size-based extra purge if still over the configured limit
        long maxBytes = (long) settings.getMaxDbSizeGb() * 1024 * 1024 * 1024;
        long dbSize = getCurrentDbSizeBytes();
        if (dbSize > maxBytes) {
            log.warn("DB size {} bytes exceeds limit {} bytes — running extra size-based purge", dbSize, maxBytes);
            Map<String, Long> extraCounts = runSizeBasedPurge(maxBytes);
            extraCounts.forEach((k, v) -> counts.merge(k, v, Long::sum));
        }

        // 3. Update last-purged timestamp
        settings.setLastPurgedAt(LocalDateTime.now());
        retentionSettingsRepository.save(settings);

        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        log.info("Retention purge completed — total deleted: {}", total);

        return new PurgeResultDto(true, settings.getLastPurgedAt(), counts, total,
                "Purge dokončen. Celkem smazáno: " + total + " záznamů.");
    }

    /**
     * Checks whether the DB is over the size limit and, if so, deletes the oldest 10 %
     * of records from the most verbose tables until the limit is satisfied.
     */
    @Transactional
    public PurgeResultDto runSizeCheck() {
        RetentionSettings settings = getSettings();
        long maxBytes = (long) settings.getMaxDbSizeGb() * 1024 * 1024 * 1024;
        long dbSize = getCurrentDbSizeBytes();

        if (dbSize <= maxBytes) {
            return new PurgeResultDto(true, LocalDateTime.now(), Map.of(), 0L,
                    "DB je v limitu (" + formatMb(dbSize) + " / " + formatMb(maxBytes) + "). Purge není potřeba.");
        }

        Map<String, Long> counts = runSizeBasedPurge(maxBytes);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        settings.setLastPurgedAt(LocalDateTime.now());
        retentionSettingsRepository.save(settings);

        return new PurgeResultDto(true, settings.getLastPurgedAt(), counts, total,
                "Size-based purge dokončen. Smazáno: " + total + " záznamů.");
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private long purgeOlderThan(String table, LocalDateTime cutoff) {
        try {
            return switch (table) {
                case "logs" -> deviceLogEntryRepository.deleteByOccurredAtBefore(cutoff);
                case "fileSystemEvents" -> fileSystemEventRepository.deleteByOccurredAtBefore(cutoff);
                case "telemetry" -> telemetrySampleRepository.deleteByCollectedAtBefore(cutoff);
                case "heartbeats" -> agentHeartbeatRepository.deleteByLastSeenAtBefore(cutoff);
                case "snapshots" -> deleteSnapshotsBefore(cutoff);
                case "detectionFindings" -> deleteFindingsBefore(cutoff);
                case "remoteSessions" -> remoteSessionRepository.deleteByStartedAtBefore(cutoff);
                case "aiRuns" -> aiAnalysisRunRepository.deleteByStartedAtBefore(cutoff);
                default -> 0L;
            };
        } catch (Exception ex) {
            log.error("Error purging table '{}': {}", table, ex.getMessage(), ex);
            return 0L;
        }
    }

    /**
     * Snapshots have child tables (network_interface, logged_in_session) — delete
     * children first via JPQL bulk-delete using subquery, then delete the snapshots.
     */
    private long deleteSnapshotsBefore(LocalDateTime cutoff) {
        List<DeviceSnapshot> toDelete = deviceSnapshotRepository.findByCollectedAtBefore(cutoff);
        if (toDelete.isEmpty()) {
            return 0L;
        }
        // Delete child records via JPQL subquery
        entityManager.createQuery(
                "DELETE FROM NetworkInterface ni WHERE ni.snapshot IN " +
                "(SELECT s FROM DeviceSnapshot s WHERE s.collectedAt < :cutoff)")
                .setParameter("cutoff", cutoff)
                .executeUpdate();
        entityManager.createQuery(
                "DELETE FROM LoggedInSession ls WHERE ls.snapshot IN " +
                "(SELECT s FROM DeviceSnapshot s WHERE s.collectedAt < :cutoff)")
                .setParameter("cutoff", cutoff)
                .executeUpdate();
        // Now delete snapshots themselves
        deviceSnapshotRepository.deleteAll(toDelete);
        return toDelete.size();
    }

    /**
     * DetectionFindings have child DetectionFindingEvent records.
     * Delete events first, then findings.
     */
    private long deleteFindingsBefore(LocalDateTime cutoff) {
        entityManager.createQuery(
                "DELETE FROM DetectionFindingEvent fe WHERE fe.finding IN " +
                "(SELECT f FROM DetectionFinding f WHERE f.lastSeenAt < :cutoff)")
                .setParameter("cutoff", cutoff)
                .executeUpdate();
        return entityManager.createQuery(
                "DELETE FROM DetectionFinding f WHERE f.lastSeenAt < :cutoff")
                .setParameter("cutoff", cutoff)
                .executeUpdate();
    }

    /**
     * Extra purge pass when DB size exceeds the configured maximum.
     * Shifts the cutoff forward in 1-day steps until within the limit or no more records.
     */
    private Map<String, Long> runSizeBasedPurge(long maxBytes) {
        Map<String, Long> counts = new LinkedHashMap<>();
        // Start from 7 days ago and work backwards towards today
        for (int daysAgo = 7; daysAgo >= 1; daysAgo--) {
            LocalDateTime extraCutoff = LocalDateTime.now().minusDays(daysAgo);
            counts.merge("logs", purgeOlderThan("logs", extraCutoff), Long::sum);
            counts.merge("fileSystemEvents", purgeOlderThan("fileSystemEvents", extraCutoff), Long::sum);
            counts.merge("telemetry", purgeOlderThan("telemetry", extraCutoff), Long::sum);
            counts.merge("heartbeats", purgeOlderThan("heartbeats", extraCutoff), Long::sum);
            counts.merge("snapshots", purgeOlderThan("snapshots", extraCutoff), Long::sum);
            entityManager.flush(); // flush so next size check reflects changes
            if (getCurrentDbSizeBytes() <= maxBytes) {
                break;
            }
        }
        return counts;
    }

    private long getCurrentDbSizeBytes() {
        Object raw = entityManager
                .createNativeQuery("SELECT pg_database_size(current_database())")
                .getSingleResult();
        return toLong(raw);
    }

    private static long toLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof BigInteger bi) return bi.longValue();
        if (value instanceof BigDecimal bd) return bd.longValue();
        if (value instanceof Number n) return n.longValue();
        return 0L;
    }

    private static String formatMb(long bytes) {
        return (bytes / (1024 * 1024)) + " MB";
    }
}

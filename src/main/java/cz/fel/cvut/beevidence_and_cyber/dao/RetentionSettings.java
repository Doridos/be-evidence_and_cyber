package cz.fel.cvut.beevidence_and_cyber.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Single-row configuration table for data retention settings.
 * Always stored with id = 1.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "retention_settings")
public class RetentionSettings {

    /** Fixed primary key — only one row is ever stored. */
    @Id
    @Column(nullable = false)
    private int id = 1;

    /** Number of days after which device telemetry / log data is eligible for purge. */
    @Column(nullable = false)
    private int retentionDays = 30;

    /** Maximum allowed total DB size in gigabytes before automatic size-based purge kicks in. */
    @Column(nullable = false)
    private int maxDbSizeGb = 70;

    /** When false, no automatic purge runs (manual purge is still available). */
    @Column(nullable = false)
    private boolean retentionEnabled = true;

    /** Timestamp of the last successful purge run (null if never run). */
    private LocalDateTime lastPurgedAt;
}

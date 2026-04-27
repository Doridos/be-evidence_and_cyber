package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.dao.TelemetrySample;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TelemetrySampleRepository extends JpaRepository<TelemetrySample, UUID> {
    List<TelemetrySample> findByDeviceOrderByCollectedAtDesc(EndpointDevice device);
    List<TelemetrySample> findByDeviceAndCollectedAtBetweenOrderByCollectedAtAsc(EndpointDevice device, LocalDateTime from, LocalDateTime to);
    long deleteByCollectedAtBefore(LocalDateTime cutoff);
}

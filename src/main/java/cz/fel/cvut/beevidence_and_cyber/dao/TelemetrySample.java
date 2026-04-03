package cz.fel.cvut.beevidence_and_cyber.dao;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "telemetry_sample")
public class TelemetrySample extends AbstractUuidEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private EndpointDevice device;

    private LocalDateTime collectedAt;

    private BigDecimal cpuUsagePct;

    private BigDecimal memoryUsagePct;

    private BigDecimal diskUsagePct;

    private Long processCount;

    private Long serviceCount;
}

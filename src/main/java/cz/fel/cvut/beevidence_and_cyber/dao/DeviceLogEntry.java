package cz.fel.cvut.beevidence_and_cyber.dao;

import cz.fel.cvut.beevidence_and_cyber.enumeration.LogSourceEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "device_log_entry")
public class DeviceLogEntry extends AbstractUuidEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private EndpointDevice device;

    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    private LogSourceEnum logSource;

    private String level;

    private String eventCode;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String rawPayload;
}

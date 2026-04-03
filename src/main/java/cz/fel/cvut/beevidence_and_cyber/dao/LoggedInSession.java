package cz.fel.cvut.beevidence_and_cyber.dao;

import cz.fel.cvut.beevidence_and_cyber.enumeration.SessionStateEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.SessionTypeEnum;
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
@Table(name = "logged_in_session")
public class LoggedInSession extends AbstractUuidEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private DeviceSnapshot snapshot;

    private String username;

    private String domain;

    @Enumerated(EnumType.STRING)
    private SessionTypeEnum sessionType;

    @Enumerated(EnumType.STRING)
    private SessionStateEnum state;

    private LocalDateTime loginTime;
}

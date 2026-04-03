package cz.fel.cvut.beevidence_and_cyber.dao;

import cz.fel.cvut.beevidence_and_cyber.enumeration.RemoteSessionProviderEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.RemoteSessionStatusEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.RemoteSessionTypeEnum;
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
@Table(name = "remote_session")
public class RemoteSession extends AbstractUuidEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "help_request_id", nullable = false)
    private RemoteHelpRequest helpRequest;

    @ManyToOne(optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User adminUser;

    @Enumerated(EnumType.STRING)
    private RemoteSessionTypeEnum sessionType;

    @Enumerated(EnumType.STRING)
    private RemoteSessionProviderEnum provider;

    @Enumerated(EnumType.STRING)
    private RemoteSessionStatusEnum status;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;
}

package cz.fel.cvut.beevidence_and_cyber.dao;

import cz.fel.cvut.beevidence_and_cyber.enumeration.HelpRequestStatusEnum;
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
@Table(name = "remote_help_request")
public class RemoteHelpRequest extends AbstractUuidEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private EndpointDevice device;

    @Column(nullable = false)
    private String requestedByUsername;

    private String requestedByDisplayName;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    private HelpRequestStatusEnum status;

    private LocalDateTime requestedAt;

    @ManyToOne
    @JoinColumn(name = "accepted_by_user_id")
    private User acceptedByUser;

    private LocalDateTime acceptedAt;
}

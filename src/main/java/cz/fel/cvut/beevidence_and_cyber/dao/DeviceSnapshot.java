package cz.fel.cvut.beevidence_and_cyber.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "device_snapshot")
public class DeviceSnapshot extends AbstractUuidEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private EndpointDevice device;

    @Column(nullable = false)
    private Integer versionNo;

    private LocalDateTime collectedAt;

    private LocalDateTime validFrom;

    private LocalDateTime validTo;

    @Column(nullable = false)
    private String hostname;

    @Column(nullable = false)
    private String osName;

    @Column(nullable = false)
    private String osVersion;

    @Column(nullable = false)
    private String osArchitecture;

    private String domainName;

    private String currentLoggedUser;

    private LocalDateTime lastBootAt;

    private String javaAgentVersion;

    @OneToMany(mappedBy = "snapshot")
    private Set<NetworkInterface> networkInterfaces = new HashSet<>();

    @OneToMany(mappedBy = "snapshot")
    private Set<LoggedInSession> loggedInSessions = new HashSet<>();
}

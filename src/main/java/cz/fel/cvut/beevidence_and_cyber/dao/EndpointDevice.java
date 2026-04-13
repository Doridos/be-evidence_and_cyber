package cz.fel.cvut.beevidence_and_cyber.dao;

import cz.fel.cvut.beevidence_and_cyber.enumeration.DeviceStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "endpoint_device")
public class EndpointDevice extends AbstractUuidEntity {

    private String assetTag;

    @Column(nullable = false)
    private String hostname;

    private String fqdn;

    private String primaryIp;

    private String site;

    private String ownerFirstName;

    private String ownerLastName;

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private DeviceOwner owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceStatusEnum status = DeviceStatusEnum.ACTIVE;

    @Column(nullable = false)
    private boolean agentInstalled;

    @Column(nullable = false)
    private boolean usbRemovableBlocked;

    private LocalDateTime discoveredAt;

    private LocalDateTime archivedAt;

    @OneToMany(mappedBy = "device")
    private Set<DeviceSnapshot> snapshots = new HashSet<>();
}

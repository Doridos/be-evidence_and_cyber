package cz.fel.cvut.beevidence_and_cyber.dao;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "network_interface")
public class NetworkInterface extends AbstractUuidEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private DeviceSnapshot snapshot;

    private String name;

    private String displayName;

    private String macAddress;

    private String ipv4;

    private String ipv6;

    private boolean isPrimary;

    private boolean up;
}

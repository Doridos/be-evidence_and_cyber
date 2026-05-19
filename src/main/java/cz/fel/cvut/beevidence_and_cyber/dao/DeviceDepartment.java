package cz.fel.cvut.beevidence_and_cyber.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "device_department",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_department_name", columnNames = "name")
)
public class DeviceDepartment extends AbstractUuidEntity {

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "department")
    private Set<EndpointDevice> devices = new HashSet<>();
}

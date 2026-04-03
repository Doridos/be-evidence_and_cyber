package cz.fel.cvut.beevidence_and_cyber.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "app_role")
public class Role extends AbstractUuidEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private boolean system;

    @OneToMany(mappedBy = "role")
    private Set<UserRoleAssignment> userAssignments = new HashSet<>();

    @OneToMany(mappedBy = "role")
    private Set<RolePermissionAssignment> permissionAssignments = new HashSet<>();
}

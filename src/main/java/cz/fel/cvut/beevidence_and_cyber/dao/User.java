package cz.fel.cvut.beevidence_and_cyber.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "app_user")
public class User extends AbstractUuidEntity {

    @Column(nullable = false, unique = true)
    private String adUsername;

    @Column(nullable = false)
    private String displayName;

    private String email;

    private String department;

    @Column(nullable = false)
    private boolean enabled = true;

    private LocalDateTime lastLoginAt;

    @Column(nullable = false)
    private String source = "AD";

    @OneToMany(mappedBy = "user")
    private Set<UserRoleAssignment> roleAssignments = new HashSet<>();
}

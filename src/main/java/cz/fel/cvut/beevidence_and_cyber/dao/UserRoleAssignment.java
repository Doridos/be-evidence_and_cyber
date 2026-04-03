package cz.fel.cvut.beevidence_and_cyber.dao;

import jakarta.persistence.Entity;
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
@Table(name = "user_role_assignment")
public class UserRoleAssignment extends AbstractUuidEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne
    @JoinColumn(name = "assigned_by")
    private User assignedBy;

    private LocalDateTime assignedAt;

    private LocalDateTime validFrom;

    private LocalDateTime validTo;
}

package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.Role;
import cz.fel.cvut.beevidence_and_cyber.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BootstrapDataService implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {
        ensureRole("USER", "User", "Default application role for authenticated users", true);
        Role manager = ensureRole("MANAGER", "Manager", "Can view system data", true);
        ensureRole("ADMIN", "Admin", "Can manage the entire system", true);
    }

    private Role ensureRole(String code, String name, String description, boolean system) {
        return roleRepository.findByCodeIgnoreCase(code).orElseGet(() -> {
            Role role = new Role();
            role.setCode(code);
            role.setName(name);
            role.setDescription(description);
            role.setSystem(system);
            return roleRepository.save(role);
        });
    }
}

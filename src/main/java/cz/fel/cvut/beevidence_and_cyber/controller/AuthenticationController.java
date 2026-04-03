package cz.fel.cvut.beevidence_and_cyber.controller;

import cz.fel.cvut.beevidence_and_cyber.dto.AuthResponse;
import cz.fel.cvut.beevidence_and_cyber.dto.LoginRequest;
import cz.fel.cvut.beevidence_and_cyber.service.AuthenticationService;
import cz.fel.cvut.beevidence_and_cyber.service.CurrentUserService;
import cz.fel.cvut.beevidence_and_cyber.service.DirectoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final CurrentUserService currentUserService;
    private final DirectoryService directoryService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authenticationService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        return ResponseEntity.ok(directoryService.toUserDto(currentUserService.requireCurrentUser()));
    }
}

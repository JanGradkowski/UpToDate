package org.example.uptodate.controller;

import org.example.uptodate.model.User;
import org.example.uptodate.repository.UserRepository;
import org.example.uptodate.services.PrivacyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
public class PrivacyApiController {

    private final UserRepository userRepository;
    private final PrivacyService privacyService;

    public PrivacyApiController(UserRepository userRepository, PrivacyService privacyService) {
        this.userRepository = userRepository;
        this.privacyService = privacyService;
    }

    // Toggle Private Account
    @PostMapping("/api/settings/privacy/toggle")
    public ResponseEntity<?> togglePrivacy(Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        currentUser.setPrivate(!currentUser.isPrivate()); // Flip the boolean
        userRepository.save(currentUser);
        return ResponseEntity.ok(Map.of("private", currentUser.isPrivate()));
    }

    // Block a User
    @PostMapping("/api/users/{username}/block")
    public ResponseEntity<?> blockUser(@PathVariable String username, Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        User targetUser = userRepository.findByUsername(username).orElseThrow();
        if (privacyService.isSameUser(currentUser, targetUser)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot block yourself");
        }

        // 1. Add to block list
        if (!privacyService.containsUser(currentUser.getBlockedUsers(), targetUser)) {
            currentUser.getBlockedUsers().add(targetUser);
        }

        // 2. Sever all existing ties
        privacyService.severConnections(currentUser, targetUser);

        userRepository.save(currentUser);
        userRepository.save(targetUser);

        return ResponseEntity.ok().build();
    }

    // Unblock a User
    @PostMapping("/api/users/{username}/unblock")
    public ResponseEntity<?> unblockUser(@PathVariable String username, Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        User targetUser = userRepository.findByUsername(username).orElseThrow();

        privacyService.removeUser(currentUser.getBlockedUsers(), targetUser);
        userRepository.save(currentUser);

        return ResponseEntity.ok().build();
    }
}

package org.example.uptodate.controller;
import org.example.uptodate.model.User;
import org.example.uptodate.repository.UserRepository;
import org.example.uptodate.services.PrivacyService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class SearchApiController {
    private UserRepository userRepository;
    private final PrivacyService privacyService;
    public SearchApiController(UserRepository userRepository, PrivacyService privacyService) {
        this.userRepository = userRepository;
        this.privacyService = privacyService;
    }
    @GetMapping("api/search")
    public List<Map<String, String>> liveSearch(@RequestParam("q") String query, Authentication authentication) {
        if (query == null || query.isEmpty()) {
            return List.of();
        }
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        return userRepository.findByUsernameContainingIgnoreCase(query.trim())
                .stream()
                .filter(user -> privacyService.canSearchUser(currentUser, user))
                .map(user -> Map.of(
                        "username", user.getUsername(),
                        "avatar", user.getProfilePictureUrl() != null ? user.getProfilePictureUrl() :"/css/default-avatar.png"
                )).collect(Collectors.toList());
    }
}

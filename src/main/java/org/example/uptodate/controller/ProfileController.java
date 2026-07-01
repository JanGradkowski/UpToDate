package org.example.uptodate.controller;
import org.example.uptodate.model.Post;
import org.example.uptodate.model.Story;
import org.example.uptodate.model.User;
import org.example.uptodate.repository.PostRepository;
import org.example.uptodate.repository.StoryRepository;
import org.example.uptodate.repository.UserRepository;
import org.example.uptodate.services.PrivacyService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class ProfileController {
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PrivacyService privacyService;
    private final StoryRepository storyRepository;
    public ProfileController(UserRepository userRepository, PostRepository postRepository, PrivacyService privacyService, StoryRepository storyRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.privacyService = privacyService;
        this.storyRepository = storyRepository;
    }
    @GetMapping("profile/{username}")
    public String showUserProfile(@PathVariable("username") String username, Model model, Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("currentUser", currentUser);

        User targetUser = userRepository.findByUsername(username).orElseThrow( () ->
                new RuntimeException("Username not found!"));
        if (!privacyService.isSameUser(currentUser, targetUser) && privacyService.isBlockedBetween(currentUser, targetUser)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("targetUser", targetUser);

        List<Post> userPosts = postRepository.findByUserOrderByCreatedAtDesc(targetUser);
        boolean canViewProfile = privacyService.canViewProfile(currentUser, targetUser);
        model.addAttribute("posts", canViewProfile ? userPosts : List.of());
        model.addAttribute("postCount", canViewProfile ? userPosts.size() : 0);
        model.addAttribute("canViewProfile", canViewProfile);
        boolean isOwner = currentUser.getUsername().equals(targetUser.getUsername());
        model.addAttribute("isOwner", isOwner);

        if (isOwner) {
            List<Post> savedPosts = postRepository.findTop1000SavedPosts(currentUser.getId());
            model.addAttribute("savedPosts", savedPosts);

            // Create a quick lookup set for the UI to know which posts in the main grid are already saved
            Set<Long> savedPostIds = savedPosts.stream().map(Post::getId).collect(Collectors.toSet());
            model.addAttribute("savedPostIds", savedPostIds);
        } else {
            model.addAttribute("savedPostIds", Collections.emptySet());
        }
        // Check if the profile we are viewing has an active story
        List<Story> activeStories  =storyRepository.findActiveStoriesByUser(targetUser, LocalDateTime.now());
        boolean hasActiveStory = !activeStories.isEmpty();
        boolean hasUnseenStory = activeStories.stream()
                .anyMatch(story -> story.getViewers().stream().noneMatch(v -> v.getId().equals(currentUser.getId())));

        model.addAttribute("hasActiveStory", hasActiveStory);
        model.addAttribute("hasUnseenStory", hasUnseenStory);

        // If it's the owner, load their entire expired Archive
        if (isOwner) {
            List<Story> allStories = storyRepository.findAllByUserOrderByCreatedAtDesc(currentUser);
            List<Story> archive = allStories.stream()
                    .filter(s -> s.getExpiresAt().isBefore(LocalDateTime.now()))
                    .toList();
            model.addAttribute("storyArchive", archive);
        }

        return "profile";
    }
}

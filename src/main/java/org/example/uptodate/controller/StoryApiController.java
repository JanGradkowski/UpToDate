package org.example.uptodate.controller;

import org.example.uptodate.model.Notification;
import org.example.uptodate.model.NotificationType;
import org.example.uptodate.model.Story;
import org.example.uptodate.model.User;
import org.example.uptodate.repository.NotificationRepository;
import org.example.uptodate.repository.StoryRepository;
import org.example.uptodate.repository.UserRepository;
import org.example.uptodate.services.FileStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stories")
public class StoryApiController {
    private final  StoryRepository storyRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final NotificationRepository notificationRepository;
    public StoryApiController(StoryRepository storyRepository, UserRepository userRepository, FileStorageService fileStorageService,  NotificationRepository notificationRepository) {
        this.storyRepository = storyRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.notificationRepository = notificationRepository;
    }
    @PostMapping("/create")
    public ResponseEntity<?> createStory(@RequestParam("media") MultipartFile media, Authentication authentication) throws IOException {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        String fileUrl = fileStorageService.saveImage(media);
        Story story = new Story();
        story.setUser(currentUser);
        story.setMediaUrl(fileUrl);
        storyRepository.save(story);
        return ResponseEntity.ok(Map.of("message", "Story uploaded successfully!"));
    }
    @PostMapping("/{id}/view")
    public ResponseEntity<?> recordView(@PathVariable Long id, Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Story story = storyRepository.findById(id).orElseThrow();
        if(!story.getUser().getId().equals(currentUser.getId())){
            story.getViewers().add(currentUser);
            storyRepository.save(story);
        }
        return ResponseEntity.ok().build();
    }
    @PostMapping("/{id}/like")
    public ResponseEntity<?> toggleLike(@PathVariable Long id, Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Story story = storyRepository.findById(id).orElseThrow();

        boolean isLiked = story.getLikers().contains(currentUser);
        if (isLiked) {
            story.getLikers().remove(currentUser);
        } else {
            story.getLikers().add(currentUser);
        }
        if (!story.getUser().getId().equals(currentUser.getId())) {
            Notification notification = new Notification();
            notification.setSender(currentUser);
            notification.setType(NotificationType.STORY_LIKE);
            notification.setReceiver(story.getUser());
            notification.setStory(story);
            notification.setCreatedAt(LocalDateTime.now());
            notification.setRead(false);

            notificationRepository.save(notification);
        }

        storyRepository.save(story);
        return ResponseEntity.ok(Map.of("liked", !isLiked));
    }

    @GetMapping("/user/{username}/active")
    public ResponseEntity<List<org.example.uptodate.dto.StoryDto>> getActiveStories(@PathVariable String username, Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        User targetUser = userRepository.findByUsername(username).orElseThrow();

        List<Story> activeStories = storyRepository.findActiveStoriesByUser(targetUser, LocalDateTime.now());
        boolean isOwner = currentUser.getId().equals(targetUser.getId());

        List<org.example.uptodate.dto.StoryDto> dtos = activeStories.stream().map(story -> {

            // Only fetch specific viewer/liker data if the requester owns the story
            List<org.example.uptodate.dto.StoryInteractionDto> viewedBy = isOwner ?
                    story.getViewers().stream()
                            .map(v -> new org.example.uptodate.dto.StoryInteractionDto(v.getUsername(), v.getProfilePictureUrl() != null ? v.getProfilePictureUrl() : "/css/default-avatar.png"))
                            .toList() : List.of();

            List<org.example.uptodate.dto.StoryInteractionDto> likedBy = isOwner ?
                    story.getLikers().stream()
                            .map(l -> new org.example.uptodate.dto.StoryInteractionDto(l.getUsername(), l.getProfilePictureUrl() != null ? l.getProfilePictureUrl() : "/css/default-avatar.png"))
                            .toList() : List.of();

            return new org.example.uptodate.dto.StoryDto(
                    story.getId(),
                    story.getMediaUrl(),
                    targetUser.getUsername(),
                    targetUser.getProfilePictureUrl() != null ? targetUser.getProfilePictureUrl() : "/css/default-avatar.png",
                    story.getViewers().size(),
                    story.getLikers().contains(currentUser),
                    isOwner,
                    viewedBy,
                    likedBy
            );
        }).toList();

        return ResponseEntity.ok(dtos);
    }
}

package org.example.uptodate.controller;

import org.example.uptodate.dto.NotificationDto;
import org.example.uptodate.model.Notification;
import org.example.uptodate.model.NotificationType;
import org.example.uptodate.model.User;
import org.example.uptodate.repository.NotificationRepository;
import org.example.uptodate.repository.UserRepository;
import org.example.uptodate.services.PrivacyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class NotificationApiController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PrivacyService privacyService;

    public NotificationApiController(NotificationRepository notificationRepository, UserRepository userRepository, PrivacyService privacyService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.privacyService = privacyService;
    }

    @GetMapping("/api/notifications")
    public ResponseEntity<?> getNotifications(Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();

        List<Notification> rawNotifications = notificationRepository.findTop50ByReceiverOrderByCreatedAtDesc(currentUser);

        List<NotificationDto> dtos = rawNotifications.stream()
                .filter(notif -> privacyService.canSearchUser(currentUser, notif.getSender()))
                .filter(notif -> notif.getPost() == null || privacyService.canViewPost(currentUser, notif.getPost()))
                .map(notif -> {
            String message = switch (notif.getType()) {
                case POST_LIKE -> "liked your post.";
                case COMMENT_LIKE -> "liked your comment.";
                case POST_COMMENT -> "commented on your post.";
                case COMMENT_REPLY -> "replied to your comment.";
                case NEW_POST -> "just posted a new photo.";
                case FOLLOW -> "started following you.";
                case FOLLOW_REQUEST -> "would like to follow you.";
                case CHAT_GIF -> "sent you a gif";
                case CHAT_IMAGE ->  "sent you an image";
                case CHAT_VIDEO -> "sent you a video";
                case CHAT_MESSAGE ->  "sent you a message";
                case VOICE_MESSAGE ->  "sent you a voice message";
                case THOUGHT -> "just posted a thought";
                case STORY_LIKE -> "liked your story.";
            };

            String link;
            if (notif.getType() == NotificationType.FOLLOW_REQUEST){
                link = "/settings#privacy";
            }
            else if (notif.getType() == NotificationType.CHAT_MESSAGE ||
                    notif.getType() == NotificationType.CHAT_IMAGE ||
                    notif.getType() == NotificationType.CHAT_GIF ||
                    notif.getType() == NotificationType.VOICE_MESSAGE ||
                    notif.getType() == NotificationType.CHAT_VIDEO) {
                link = "/direct/" + notif.getSender().getUsername();
            }
            else {
                link = (notif.getPost() != null) ? "/post/" + notif.getPost().getId() : "/profile/" + notif.getSender().getUsername();
            }
            String avatar = notif.getSender().getProfilePictureUrl() != null ? notif.getSender().getProfilePictureUrl() : "/css/default-avatar.png";

            return new NotificationDto(notif.getId(), notif.getSender().getUsername(), avatar, message, link, notif.isRead());
        }).collect(Collectors.toList());
        long unreadCount = dtos.stream().filter(notification -> !notification.isRead()).count();

        return ResponseEntity.ok(Map.of("unreadCount", unreadCount, "notifications", dtos));
    }

    @PostMapping("/api/notifications/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable("id") Long id, Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Notification notification = notificationRepository.findById(id).orElseThrow();
        if (!privacyService.isSameUser(currentUser, notification.getReceiver())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        notification.setRead(true);
        notificationRepository.save(notification);
        return ResponseEntity.ok().build();
    }
    @PostMapping("/api/notifications/mark-all-read")
    public ResponseEntity<?> markAllAsRead(Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        List<Notification> unreadNotifications = notificationRepository.findByReceiverAndIsReadFalse(currentUser);
        unreadNotifications.forEach(notification -> {notification.setRead(true);});
        notificationRepository.saveAll(unreadNotifications);
        return ResponseEntity.ok().build();
    }
}

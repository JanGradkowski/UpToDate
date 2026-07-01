package org.example.uptodate.services;
import org.example.uptodate.model.Notification;
import org.example.uptodate.model.NotificationType;
import org.example.uptodate.model.Post;
import org.example.uptodate.model.User;
import org.example.uptodate.repository.NotificationRepository;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private NotificationRepository notificationRepository;
    private final PrivacyService privacyService;
    public NotificationService(NotificationRepository notificationRepository, PrivacyService privacyService) {
        this.notificationRepository = notificationRepository;
        this.privacyService = privacyService;
    }
    public void sendNotification(User sender, User receiver, NotificationType type,Post post) {
        if (sender.getId().equals(receiver.getId())) {
            return;
        }
        if (privacyService.isBlockedBetween(sender, receiver)) {
            return;
        }
        if (post != null && !privacyService.canViewPost(receiver, post)) {
            return;
        }
        Notification notification = new Notification();
        notification.setSender(sender);
        notification.setReceiver(receiver);
        notification.setType(type);
        if (post != null) {
            notification.setPost(post);
        }
        notificationRepository.save(notification);
    }
}

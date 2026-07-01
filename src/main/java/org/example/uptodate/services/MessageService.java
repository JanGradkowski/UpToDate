package org.example.uptodate.services;
import org.example.uptodate.model.*;
import org.example.uptodate.repository.ConversationRepository;
import org.example.uptodate.repository.MessageRepository;
import org.example.uptodate.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import javax.print.attribute.standard.Media;
import java.time.LocalDateTime;


@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final PrivacyService privacyService;
    private final NotificationService notificationService;

    public MessageService(MessageRepository messageRepository, UserRepository userRepository, ConversationRepository conversationRepository, PrivacyService privacyService, NotificationService notificationService) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.privacyService = privacyService;
        this.notificationService = notificationService;
    }

    public Conversation getOrCreateConversation(User sender, User recipient) {
        return conversationRepository.findExistingConversation(sender, recipient)
                .orElseGet(() -> {
                    Conversation conversation = new Conversation();
                    conversation.getParticipants().add(sender);
                    conversation.getParticipants().add(recipient);
                    if (!privacyService.routesToInboxDirectly(sender, recipient)) {
                        conversation.setPending(true);
                        conversation.setInitiator(sender);
                    }
                    return conversationRepository.save(conversation);
                });
    }
    public void sendMessage(User sender, User recipient, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Conversation conversation = getOrCreateConversation(sender, recipient);
        Message message = new Message();
        message.setSender(sender);
        message.setContent(text.trim());
        message.setType(MessageType.TEXT);
        message.setConversation(conversation);

        conversation.getMessages().add(message);

        conversation.setUpdatedAt(LocalDateTime.now());
        message.setCreatedAt(LocalDateTime.now());

        messageRepository.save(message);
        conversationRepository.save(conversation);
        notificationService.sendNotification(sender, recipient, NotificationType.CHAT_MESSAGE, null);
    }

    public void sendMediaMessage(User sender, User recipient, MessageType type,  String mediaUrl, String mediaContentType, String caption, Integer durationSeconds) {
        if (mediaUrl == null || mediaUrl.isEmpty()) {
            throw new IllegalArgumentException("Invalid media message");
        }
        Conversation conversation = getOrCreateConversation(sender, recipient);
        Message message = new Message();
        message.setSender(sender);
        message.setType(type);
        message.setMediaUrl(mediaUrl);
        message.setMediaContentType(mediaContentType);
        message.setDurationSeconds(durationSeconds);
        if (caption != null && !caption.trim().isEmpty()) {
            message.setContent(caption.trim());
        } else {
            message.setContent(" ");
        }

        message.setConversation(conversation);

        // Ensure the in-memory relationship is synced
        conversation.getMessages().add(message);

        conversation.setUpdatedAt(LocalDateTime.now());
        message.setCreatedAt(LocalDateTime.now());

        messageRepository.save(message);
        conversationRepository.save(conversation);
        NotificationType notificationType =  switch (type){
            case IMAGE ->  NotificationType.CHAT_IMAGE;
            case VIDEO ->  NotificationType.CHAT_VIDEO;
            case GIF ->   NotificationType.CHAT_GIF;
            case VOICE -> NotificationType.VOICE_MESSAGE;
            default -> NotificationType.CHAT_MESSAGE;
        };
        notificationService.sendNotification(sender, recipient, notificationType, null);
    }
}

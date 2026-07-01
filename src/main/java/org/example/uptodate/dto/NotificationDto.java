package org.example.uptodate.dto;

public record NotificationDto(
        Long id,
        String senderUsername,
        String senderAvatar,
        String message,
        String link,
        boolean isRead
) {}
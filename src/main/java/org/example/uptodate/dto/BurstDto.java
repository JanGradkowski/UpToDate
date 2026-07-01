package org.example.uptodate.dto;

public record BurstDto(
        Long id,
        String videoUrl,
        String caption,
        String username,
        String userAvatar,
        int likesCount,
        int commentsCount,
        boolean isLikedByMe
) {}
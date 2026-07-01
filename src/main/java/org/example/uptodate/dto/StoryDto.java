package org.example.uptodate.dto;

import java.util.List;

public record StoryDto(
        Long id,
        String mediaUrl,
        String username,
        String userAvatar,
        int viewCount,
        boolean isLikedByMe,
        boolean isOwner,
        List<StoryInteractionDto> viewedBy,
        List<StoryInteractionDto> likedBy
) {}
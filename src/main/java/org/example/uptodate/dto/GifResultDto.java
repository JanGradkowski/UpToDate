package org.example.uptodate.dto;


public record GifResultDto(
        String id,
        String previewUrl,   // small/looping preview, shown in the search grid
        String fullUrl,      // the actual GIF file URL to send in the message
        String description   // alt text, for accessibility
) {}
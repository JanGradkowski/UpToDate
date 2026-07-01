package org.example.uptodate.services;

import org.example.uptodate.dto.GifResultDto;

import java.util.List;


public interface GifProviderService {
    List<GifResultDto> search(String query, int limit);

    /**
     * Trending/featured GIFs to show before the user has typed a search term.
     */
    List<GifResultDto> trending(int limit);
}
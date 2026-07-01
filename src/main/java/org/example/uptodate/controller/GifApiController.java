package org.example.uptodate.controller;

import org.example.uptodate.dto.GifResultDto;
import org.example.uptodate.services.GifProviderService;
import org.example.uptodate.services.KlipyGifProviderService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
public class GifApiController {

    private final GifProviderService gifProviderService;

    public GifApiController(GifProviderService gifProviderService) {
        this.gifProviderService = gifProviderService;
    }

    @GetMapping("/api/gifs/search")
    public List<GifResultDto> search(@RequestParam(value = "q", required = false) String query,
                                     Authentication authentication) {
        if (gifProviderService instanceof KlipyGifProviderService klipy) {
            return klipy.search(query, 24, authentication.getName());
        }
        return gifProviderService.search(query, 24);
    }

    @GetMapping("/api/gifs/trending")
    public List<GifResultDto> trending(Authentication authentication) {
        if (gifProviderService instanceof KlipyGifProviderService klipy) {
            return klipy.trending(24, authentication.getName());
        }
        return gifProviderService.trending(24);
    }
}
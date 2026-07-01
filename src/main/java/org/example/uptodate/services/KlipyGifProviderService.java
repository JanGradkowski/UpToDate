package org.example.uptodate.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.uptodate.dto.GifResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "gif.provider", havingValue = "klipy", matchIfMissing = true)
public class KlipyGifProviderService implements GifProviderService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(KlipyGifProviderService.class);

    @Value("${gif.klipy.api-key:}")
    private String apiKey;

    private static final String BASE_URL = "https://api.klipy.com/api/v1";

    public KlipyGifProviderService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(BASE_URL).build();
    }

    @PostConstruct
    void warnIfNotConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("KLIPY GIF provider is active (gif.provider=klipy) but gif.klipy.api-key is "
                    + "not set. GIF search/trending will return empty results until a key is "
                    + "configured.");
        }
    }

    @Override
    public List<GifResultDto> search(String query, int limit) {
        return search(query, limit, "anonymous");
    }

    @Override
    public List<GifResultDto> trending(int limit) {
        return trending(limit, "anonymous");
    }

    public List<GifResultDto> search(String query, int limit, String customerId) {
        if (apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return trending(limit, customerId);
        }

        String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        String encodedCustomerId = URLEncoder.encode(safeCustomerId(customerId), StandardCharsets.UTF_8);
        String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

        String uri = String.format(
                "/%s/gifs/search?q=%s&customer_id=%s&per_page=%d&page=1",
                encodedApiKey, encodedQuery, encodedCustomerId, clamp(limit));

        return fetchAndParse(uri);
    }

    public List<GifResultDto> trending(int limit, String customerId) {
        if (apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        String encodedCustomerId = URLEncoder.encode(safeCustomerId(customerId), StandardCharsets.UTF_8);
        String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

        String uri = String.format(
                "/%s/gifs/trending?customer_id=%s&per_page=%d&page=1",
                encodedApiKey, encodedCustomerId, clamp(limit));

        return fetchAndParse(uri);
    }

    private String safeCustomerId(String customerId) {
        return (customerId == null || customerId.isBlank()) ? "anonymous" : customerId;
    }

    private int clamp(int limit) {
        return Math.min(Math.max(limit, 1), 50);
    }

    private List<GifResultDto> fetchAndParse(String uri) {
        try {
            String rawJson = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> response.createException())
                    .bodyToMono(String.class)
                    .block();

            return parseResults(rawJson);
        } catch (Exception e) {
            System.out.println("--- KLIPY API ERROR ---");
            System.out.println(e.getMessage());
            return List.of();
        }
    }

    private List<GifResultDto> parseResults(String rawJson) throws Exception {
        List<GifResultDto> out = new ArrayList<>();
        if (rawJson == null) {
            return out;
        }
        JsonNode root = objectMapper.readTree(rawJson);

        JsonNode itemsArray = root.path("data").path("data");
        if (!itemsArray.isArray()) {
            itemsArray = root.path("data");
        }
        if (!itemsArray.isArray()) {
            itemsArray = root.path("results");
        }

        for (JsonNode item : itemsArray) {
            String id = firstNonEmpty(
                    item.path("id").asText(""),
                    item.path("slug").asText("")
            );
            String description = firstNonEmpty(
                    item.path("title").asText(""),
                    item.path("content_description").asText(""),
                    "GIF"
            );

            // Use deep searching to bypass structural changes in the API response payload
            List<JsonNode> allUrls = item.findValues("url");
            String bestUrl = null;

            // Step 1: Look specifically for a rendered media file extension
            for (JsonNode urlNode : allUrls) {
                String urlStr = urlNode.asText();
                if (urlStr.startsWith("http") && (urlStr.contains(".gif") || urlStr.contains(".mp4"))) {
                    bestUrl = urlStr;
                    break;
                }
            }

            // Step 2: Fallback to the first available absolute link string if no clear extension matches
            if (bestUrl == null && !allUrls.isEmpty()) {
                for (JsonNode urlNode : allUrls) {
                    String urlStr = urlNode.asText();
                    if (urlStr.startsWith("http")) {
                        bestUrl = urlStr;
                        break;
                    }
                }
            }

            if (bestUrl != null) {
                out.add(new GifResultDto(id, bestUrl, bestUrl, description));
            }
        }

        if (itemsArray.isArray() && !itemsArray.isEmpty() && out.isEmpty()) {
            log.warn("KLIPY returned data but deep scanning failed to find any valid image URLs. Sample element structure:\n{}",
                    itemsArray.get(0).toPrettyString());
        }

        return out;
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }
}
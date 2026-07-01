package org.example.uptodate.controller;

import org.example.uptodate.dto.BurstDto;
import org.example.uptodate.model.MediaType;
import org.example.uptodate.model.Post;
import org.example.uptodate.model.User;
import org.example.uptodate.repository.PostRepository;
import org.example.uptodate.repository.UserRepository;
import org.example.uptodate.services.PrivacyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class BurstApiController {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PrivacyService privacyService;

    public BurstApiController(PostRepository postRepository, UserRepository userRepository, PrivacyService privacyService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.privacyService = privacyService;
    }

    // --- MAIN FEED TELEMETRY (Dwell Time) ---
    @PostMapping("/api/posts/{id}/view")
    public ResponseEntity<?> recordPostView(@PathVariable Long id, Authentication authentication) {
        Post post = postRepository.findById(id).orElseThrow();
        post.setCompletions(post.getCompletions() + 1);

        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Map<String, Integer> weights = currentUser.getInterestWeights();

        for (org.example.uptodate.model.Hashtag tag : post.getHashtags()) {
            String tagWord = tag.getName().toLowerCase();
            // Standard feed views are worth 5 points instead of 10 to balance the scale
            weights.put(tagWord, weights.getOrDefault(tagWord, 0) + 5);
        }

        postRepository.save(post);
        userRepository.save(currentUser);
        return ResponseEntity.ok().build();
    }

    // --- TELEMETRY ENDPOINT ---
    // The frontend silently hits this when a video plays to 95% completion
    @PostMapping("/api/bursts/{id}/watch")
    public ResponseEntity<?> recordWatchTime(@PathVariable Long id, Authentication authentication) {
        Post post = postRepository.findById(id).orElseThrow();
        post.setCompletions(post.getCompletions() + 1);
        postRepository.save(post);

        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Map<String, Integer> weights = currentUser.getInterestWeights();

        // Add 10 points for every hashtag on the watched video
        for (org.example.uptodate.model.Hashtag tag : post.getHashtags()) {
            String tagWord = tag.getName().toLowerCase();
            weights.put(tagWord, weights.getOrDefault(tagWord, 0) + 10);
        }

        userRepository.save(currentUser);
        return ResponseEntity.ok().build();
    }

    private static class ScoredBurst {
        Post post;
        long score;
        ScoredBurst(Post post, long score) {
            this.post = post;
            this.score = score;
        }
    }

    @GetMapping("/api/bursts/next")
    public List<BurstDto> getNextBursts(
            @RequestParam(value = "seenIds", required = false) List<Long> seenIds,
            Authentication authentication) {

        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Set<Long> excludedIds = seenIds != null ? new HashSet<>(seenIds) : new HashSet<>();

        // 1. Fetch all posts to build the affinity map and the pool of REELS
        List<Post> allPosts = postRepository.findAll();

        // Build Creator Affinity Graph (How many posts has this user liked from specific authors?)
        Map<String, Long> creatorAffinityMap = allPosts.stream()
                .filter(p -> p.isLikedBy(currentUser))
                .collect(Collectors.groupingBy(p -> p.getUser().getUsername(), Collectors.counting()));

        // Filter down to just the REELS the user is allowed to see and hasn't seen yet
        List<Post> availableReels = allPosts.stream()
                .filter(p -> p.getMediaType() == MediaType.REEL)
                .filter(p -> !excludedIds.contains(p.getId()))
                .filter(p -> privacyService.canViewPost(currentUser, p))
                .collect(Collectors.toList());

        if (availableReels.isEmpty()) return List.of();

        List<Post> zeroEngagementWildcards = new ArrayList<>();
        List<ScoredBurst> scorablePool = new ArrayList<>();
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);

        // Clean up user interests for reliable matching
        // --- THE RELATIVE THRESHOLD FILTER (Top 5 Interests) ---
        // Instead of a hard '50' limit, sort their entire map from highest score to lowest
        // and extract ONLY their top 5 highest-scoring hashtags relative to everything else.
        List<String> coreInterests = currentUser.getInterestWeights().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        for (Post reel : availableReels) {
            if (reel.getLikedByUsers().isEmpty() && reel.getCompletions() == 0) {
                zeroEngagementWildcards.add(reel);
                continue;
            }

            long score = reel.getLikedByUsers().size()
                    + (reel.getComments().size() * 3L)
                    + (reel.getCompletions() * 10L);

            if (reel.getCreatedAt().isAfter(yesterday)) {
                score *= 2;
            }

            // Cross-reference against their CORE interests (Score > 50)
            boolean matchesInterest = reel.getHashtags().stream()
                    .anyMatch(tag -> coreInterests.contains(tag.getName().toLowerCase()));
            if (matchesInterest) {
                score *= 3;
            }



            // SIGNAL 4: Creator Affinity (Social Graph)
            long priorLikes = creatorAffinityMap.getOrDefault(reel.getUser().getUsername(), 0L);
            if (priorLikes > 0) {
                // If they've liked 2 posts from this person before, the score multiplies by 3
                score *= (1 + priorLikes);
            }

            scorablePool.add(new ScoredBurst(reel, score));
        }

        // Sort by final calculated score
        scorablePool.sort((a, b) -> Long.compare(b.score, a.score));

        // 3. Build the batch
        List<Post> nextBatch = new ArrayList<>();
        for (int i = 0; i < Math.min(4, scorablePool.size()); i++) {
            nextBatch.add(scorablePool.get(i).post);
        }

        // 4. Inject 1 Random Wildcard for discovery
        if (!zeroEngagementWildcards.isEmpty()) {
            Collections.shuffle(zeroEngagementWildcards);
            nextBatch.add(zeroEngagementWildcards.get(0));
        } else if (scorablePool.size() >= 5) {
            nextBatch.add(scorablePool.get(4).post);
        }

        Collections.shuffle(nextBatch); // Shuffle so wildcard isn't always last

        return nextBatch.stream().map(post -> new BurstDto(
                post.getId(),
                post.getFirstImageUrl(),
                post.getCaption(),
                post.getUser().getUsername(),
                post.getUser().getProfilePictureUrl() != null ? post.getUser().getProfilePictureUrl() : "/css/default-avatar.png",
                post.getLikedByUsers().size(),
                post.getComments().size(),
                post.isLikedBy(currentUser)
        )).collect(Collectors.toList());
    }
}
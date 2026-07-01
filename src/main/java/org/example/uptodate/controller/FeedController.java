package org.example.uptodate.controller;
import org.example.uptodate.model.MediaType;
import org.example.uptodate.model.Post;
import org.example.uptodate.model.User;
import org.example.uptodate.repository.PostRepository;
import org.example.uptodate.repository.StoryRepository;
import org.example.uptodate.repository.UserRepository;
import org.example.uptodate.services.PrivacyService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class FeedController {
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PrivacyService privacyService;
    private final StoryRepository storyRepository;
    public FeedController(UserRepository userRepository, PostRepository postRepository, PrivacyService privacyService, StoryRepository storyRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.privacyService = privacyService;
        this.storyRepository = storyRepository;
    }
    @GetMapping("/feed")
    public String showFeed(
            @RequestParam(value = "search", required = false) String searchQuery,
            Authentication authentication,
            Model model
    ) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("currentUser", currentUser);

        // --- 1. STORY TRAY LOGIC (Moved outside the Search block!) ---
        List<User> usersWithStories = new ArrayList<>();
        if (!currentUser.getFollowing().isEmpty()) {
            usersWithStories = userRepository.findFollowedUsersWithUnseenActiveStories(currentUser.getId(), LocalDateTime.now());
        }
        model.addAttribute("usersWithStories", usersWithStories);

        // --- 2. SEARCH LOGIC ---
        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            List<User> searchResults = userRepository.findByUsernameContainingIgnoreCase(searchQuery.trim());
            searchResults = searchResults.stream()
                    .filter(user -> privacyService.canSearchUser(currentUser, user))
                    .toList();
            model.addAttribute("searchResults", searchResults);
            model.addAttribute("searchQuery", searchQuery);
            model.addAttribute("posts", java.util.Collections.emptyList());

            return "feed";
        }

        // --- 3. FETCH POSTS & BUILD AFFINITY MAPS ---
        List<Post> allPosts = postRepository.findAll();

        Map<String, Long> creatorAffinityMap = allPosts.stream()
                .filter(p -> p.isLikedBy(currentUser))
                .collect(java.util.stream.Collectors.groupingBy(p -> p.getUser().getUsername(), java.util.stream.Collectors.counting()));

        List<String> coreInterests = currentUser.getInterestWeights().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        List<Post> visiblePosts = allPosts.stream()
                .filter(post -> post.getMediaType() != org.example.uptodate.model.MediaType.REEL)
                .filter(post -> privacyService.canViewPost(currentUser, post))
                .toList();

        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);

        class ScoredPost {
            Post post; long score;
            ScoredPost(Post p, long s) { post = p; score = s; }
        }

        // --- 4. THE MAIN FEED ALGORITHM ---
        List<ScoredPost> scoredPool = new ArrayList<>();

        for (Post post : visiblePosts) {
            long score = 10L
                    + (post.getLikedByUsers().size() * 5L)
                    + (post.getComments().size() * 10L)
                    + (post.getCompletions() * 2L);

            if (currentUser.isFollowing(post.getUser()) || currentUser.getId().equals(post.getUser().getId())) {
                score *= 10;
            }

            if (post.getCreatedAt().isAfter(yesterday)) {
                score *= 3;
            }

            boolean matchesInterest = post.getHashtags().stream()
                    .anyMatch(tag -> coreInterests.contains(tag.getName().toLowerCase()));
            if (matchesInterest) {
                score *= 2;
            }

            long priorLikes = creatorAffinityMap.getOrDefault(post.getUser().getUsername(), 0L);
            if (priorLikes > 0) {
                score *= (1 + priorLikes);
            }

            scoredPool.add(new ScoredPost(post, score));
        }

        scoredPool.sort((a, b) -> Long.compare(b.score, a.score));

        List<Post> finalFeed = scoredPool.stream()
                .limit(50)
                .map(sp -> sp.post)
                .toList();

        model.addAttribute("posts", finalFeed);

        Set<Long> savedPostIds = currentUser.getSavedPosts().stream()
                .map(Post::getId)
                .collect(Collectors.toSet());
        model.addAttribute("savedPostIds", savedPostIds);

        return "feed";
    }
    @GetMapping("/bursts")
    public String showBursts(Model model, Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("currentUser", currentUser);
        return "bursts";
    }
}

    package org.example.uptodate.controller;

    import org.example.uptodate.model.Hashtag;
    import org.example.uptodate.model.MediaType;
    import org.example.uptodate.model.Post;
    import org.example.uptodate.model.User;
    import org.example.uptodate.repository.HashtagRepository;
    import org.example.uptodate.repository.PostRepository;
    import org.example.uptodate.repository.UserRepository;
    import org.springframework.http.ResponseEntity;
    import org.springframework.security.core.Authentication;
    import org.springframework.web.bind.annotation.GetMapping;
    import org.springframework.web.bind.annotation.RestController;

    import java.util.*;

    @RestController
    public class DataSeederController {

        private final PostRepository postRepository;
        private final UserRepository userRepository;
        private final HashtagRepository hashtagRepository;

        public DataSeederController(PostRepository postRepository, UserRepository userRepository, HashtagRepository hashtagRepository) {
            this.postRepository = postRepository;
            this.userRepository = userRepository;
            this.hashtagRepository = hashtagRepository;
        }

        @GetMapping("/api/admin/seed-bursts")
        public ResponseEntity<String> seedBursts(Authentication authentication) {
            User author = userRepository.findByUsername(authentication.getName()).orElseThrow();
            Random random = new Random();

            // 1. Core Data Pools
            List<String> ALL_CATEGORIES = Arrays.asList("photography", "travel", "technology", "fashion", "food", "sports", "art");

            List<String> TEST_VIDEOS = Arrays.asList(
                    "https://www.w3schools.com/html/mov_bbb.mp4", // Big Buck Bunny
                    "https://media.w3.org/2010/05/sintel/trailer.mp4", // Sintel
                    "https://media.w3.org/2010/05/video/movie_300.mp4" // W3C Sample
            );

            // 2. Generate 10 Bots with Specific Interests
            // 2. Generate 10 Bots with Specific Interests
            List<User> bots = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                String botName = "test_bot_" + i;
                User bot = userRepository.findByUsername(botName).orElseGet(() -> {
                    User newBot = new User();
                    newBot.setUsername(botName);
                    newBot.setEmail(botName + "@test.com");
                    newBot.setPassword("password");
                    newBot.setIfProfileSetUp(true);

                    Collections.shuffle(ALL_CATEGORIES);
                    Map<String, Integer> botWeights = new HashMap<>();

                    // Give the bot a massive starting score (100) in two random categories
                    botWeights.put(ALL_CATEGORIES.get(0), 100);
                    botWeights.put(ALL_CATEGORIES.get(1), 100);

                    newBot.setInterestWeights(botWeights);

                    return userRepository.save(newBot);
                });
                bots.add(bot);
            }

            // 3. Pre-fetch/Create Hashtags so we don't hit the DB 3000 times in the loop
            List<Hashtag> dbHashtags = new ArrayList<>();
            for (String cat : ALL_CATEGORIES) {
                Hashtag tag = hashtagRepository.findByName(cat).orElseGet(() -> {
                    Hashtag newTag = new Hashtag();
                    newTag.setName(cat);
                    return hashtagRepository.save(newTag);
                });
                dbHashtags.add(tag);
            }

            // 4. Generate 1000 Highly Varied Bursts
            List<Post> dummyPosts = new ArrayList<>();

            for (int i = 1; i <= 1000; i++) {
                Post post = new Post();
                post.setUser(author);
                post.setMediaType(MediaType.REEL);
                post.setCaption("Algorithm Stress Test Video #" + i);

                // Assign a random video
                post.setImageUrls(List.of(TEST_VIDEOS.get(random.nextInt(TEST_VIDEOS.size()))));

                // Assign random watch-time completions (0 to 5)
                post.setCompletions(random.nextInt(6));

                // Assign 1 to 3 random hashtags
                Collections.shuffle(dbHashtags);
                int tagCount = random.nextInt(3) + 1; // 1, 2, or 3
                List<Hashtag> postTags = new ArrayList<>();
                for (int t = 0; t < tagCount; t++) {
                    postTags.add(dbHashtags.get(t));
                }
                post.setHashtags(postTags);

                // Assign random likes from bots
                for (User bot : bots) {
                    if (random.nextBoolean()) {
                        post.getLikedByUsers().add(bot);
                    }
                }

                dummyPosts.add(post);
            }

            // 5. Save all 1000 posts to Oracle
            postRepository.saveAll(dummyPosts);

            return ResponseEntity.ok("Successfully generated 10 smart bots and 1,000 diverse Bursts!");
        }
    }
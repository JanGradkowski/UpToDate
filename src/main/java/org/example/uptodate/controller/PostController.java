package org.example.uptodate.controller;

import org.apache.coyote.Response;
import org.example.uptodate.model.*;
import org.example.uptodate.repository.HashtagRepository;
import org.example.uptodate.repository.PostRepository;
import org.example.uptodate.repository.UserRepository;
import org.example.uptodate.services.FileStorageService;
import org.example.uptodate.services.NotificationService;
import org.example.uptodate.services.PrivacyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Controller
public class PostController {
    private final NotificationService notificationService;
    private HashtagRepository hashtagRepository;
    private PostRepository postRepository;
    private UserRepository userRepository;
    private FileStorageService fileStorageService;
    private final PrivacyService privacyService;

    public PostController(HashtagRepository hashtagRepository, PostRepository postRepository, UserRepository userRepository, FileStorageService fileStorageService, NotificationService notificationService, PrivacyService privacyService) {
        this.hashtagRepository = hashtagRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.privacyService = privacyService;
    }

    @GetMapping("/post/create")
    public String showCreatePostForm() {
        return "create-post";
    }

    @PostMapping("/api/posts/{id}/save")
    @ResponseBody
    public ResponseEntity<?> toggleSavePost(@PathVariable Long id, Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Post post = postRepository.findById(id).orElseThrow();

        // 1. Check for a match using the actual database ID, not the memory reference
        boolean isAlreadySaved = currentUser.getSavedPosts().stream()
                .anyMatch(p -> p.getId().equals(post.getId()));

        if (isAlreadySaved) {
            // 2. Remove the specific post that matches this ID
            currentUser.getSavedPosts().removeIf(p -> p.getId().equals(post.getId()));
        } else {
            currentUser.getSavedPosts().add(post);
        }

        userRepository.save(currentUser);

        // Return the new state so the frontend can update the icon
        return ResponseEntity.ok(Map.of("saved", !isAlreadySaved));
    }

    @PostMapping("/post/create")
    public String createPost(Authentication authentication,
                             @RequestParam(value = "images", required = false) List<MultipartFile> images,
                             @RequestParam("caption") String caption,
                             @RequestParam(value = "postType", defaultValue = "IMAGE") String postType) throws IOException {

        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Post post = new Post();
        post.setUser(currentUser);

        if ("THOUGHT".equals(postType)) {
            if (caption == null || caption.trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thought cannot be empty.");
            }
            if (caption.length() > 280) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thoughts strictly cannot exceed 280 characters.");
            }
            post.setMediaType(MediaType.IMAGE); // Thoughts route to standard feed
        } else if ("REEL".equals(postType)) {
            post.setMediaType(MediaType.REEL); // Routes to the Bursts tab
        } else {
            post.setMediaType(MediaType.IMAGE);
        }

        post.setCaption(caption);

        List<String> savedImageURLS = new ArrayList<>();
        if (!"THOUGHT".equals(postType) && images != null) {
            for (MultipartFile image : images) {
                if(!image.isEmpty()) {
                    String URL = fileStorageService.saveImage(image);
                    savedImageURLS.add(URL);
                }
            }
        }
        post.setImageUrls(savedImageURLS);



        List<Hashtag> savedHashtags = new ArrayList<>();
        Matcher matcher = Pattern.compile("#\\w+").matcher(caption);
        while (matcher.find()) {
            String tagWord = matcher.group().substring(1).toLowerCase();
            Hashtag hashtag = hashtagRepository.findByName(tagWord).orElseGet(()-> {
                Hashtag newTag = new Hashtag();
                newTag.setName(tagWord);
                return hashtagRepository.save(newTag);
            });
            savedHashtags.add(hashtag);
        }
        post.setHashtags(savedHashtags);
        postRepository.save(post);
        NotificationType notificationType  = "THOUGHT".equals(postType)
                                            ? NotificationType.THOUGHT
                                            : NotificationType.NEW_POST;

        for (User follower: currentUser.getFollowers()) {
            notificationService.sendNotification(currentUser, follower, notificationType, post);
        }
        return "redirect:/profile/" + currentUser.getUsername();
    }

    @GetMapping("/post/{id}")
    public String showPost(@PathVariable Long id, Model model, Authentication authentication) {
        Post post = postRepository.findById(id).orElseThrow();
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();


        Set<Long> savedPostIds = currentUser.getSavedPosts().stream()
                .map(Post::getId)
                .collect(Collectors.toSet());
        model.addAttribute("savedPostIds", savedPostIds);

        model.addAttribute("post", post);
        model.addAttribute("currentUser", currentUser);

        return "post-detail";
    }
}

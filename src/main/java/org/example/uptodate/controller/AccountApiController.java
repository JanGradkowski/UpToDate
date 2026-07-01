package org.example.uptodate.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.uptodate.model.*;
import org.example.uptodate.repository.*;
import org.example.uptodate.services.PrivacyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class AccountApiController {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final NotificationRepository notificationRepository;
    private final PrivacyService privacyService;
    private final HashtagRepository hashtagRepository;

    // Single, clean constructor with all dependencies
    public AccountApiController(UserRepository userRepository, PostRepository postRepository, CommentRepository commentRepository, NotificationRepository notificationRepository, PrivacyService privacyService, HashtagRepository hashtagRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.notificationRepository = notificationRepository;
        this.privacyService = privacyService;
        this.hashtagRepository = hashtagRepository;
    }

    // --- MANAGE POSTS ---
    @PostMapping("/api/posts/{id}/delete")
    public ResponseEntity<?> deletePost(@PathVariable("id") Long id, Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Post post = postRepository.findById(id).orElseThrow();

        if (!privacyService.isSameUser(currentUser, post.getUser())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        List<Notification> postNotifs = notificationRepository.findAll().stream()
                .filter(n -> n.getPost() != null && n.getPost().getId().equals(post.getId()))
                .toList();
        notificationRepository.deleteAll(postNotifs);

        postRepository.delete(post);
        return ResponseEntity.ok().build();
    }

    // --- EDIT POST ---
    @PostMapping("/api/posts/{id}/edit")
    public ResponseEntity<?> editPost(@PathVariable("id") Long id, @RequestParam("caption") String caption, Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Post post = postRepository.findById(id).orElseThrow();

        if (!privacyService.isSameUser(currentUser, post.getUser())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        post.setCaption(caption);

        List<Hashtag> savedHashtags = new ArrayList<>();
        Matcher matcher = Pattern.compile("#\\w+").matcher(caption);
        while (matcher.find()) {
            String tagWord = matcher.group().substring(1).toLowerCase();
            Hashtag hashtag = hashtagRepository.findByName(tagWord).orElseGet(() -> {
                Hashtag newTag = new Hashtag();
                newTag.setName(tagWord);
                return hashtagRepository.save(newTag);
            });
            savedHashtags.add(hashtag);
        }

        post.setHashtags(savedHashtags);
        postRepository.save(post);

        return ResponseEntity.ok().build();
    }

    // --- DANGER ZONE ---
    @PostMapping("/api/settings/account/delete")
    public ResponseEntity<?> deleteAccount(Authentication authentication, HttpServletRequest request) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();

        List<Notification> notifications = notificationRepository.findAll().stream()
                .filter(n -> n.getSender().getId().equals(currentUser.getId()) || n.getReceiver().getId().equals(currentUser.getId()))
                .toList();
        notificationRepository.deleteAll(notifications);

        for (Post post : postRepository.findAll()) {
            if (post.isLikedBy(currentUser)) {
                privacyService.removeUser(post.getLikedByUsers(), currentUser);
                postRepository.save(post);
            }
        }

        for (Comment comment : commentRepository.findAll()) {
            if (comment.isLikedBy(currentUser)) {
                privacyService.removeUser(comment.getLikedByUsers(), currentUser);
                commentRepository.save(comment);
            }
        }

        for (User otherUser : userRepository.findAll()) {
            privacyService.severConnections(currentUser, otherUser);
            userRepository.save(otherUser);
        }

        userRepository.delete(currentUser);
        request.getSession().invalidate();
        SecurityContextHolder.clearContext();

        return ResponseEntity.ok().build();
    }
}
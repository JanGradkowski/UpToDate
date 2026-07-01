package org.example.uptodate.controller;
import org.example.uptodate.dto.UserDto;
import org.example.uptodate.model.Comment;
import org.example.uptodate.model.Post;
import org.example.uptodate.repository.CommentRepository;
import org.example.uptodate.repository.PostRepository;
import org.example.uptodate.repository.UserRepository;
import org.example.uptodate.model.User;
import org.example.uptodate.services.PrivacyService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;
@RestController
public class LikeApiController {
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PrivacyService privacyService;
    public LikeApiController(UserRepository userRepository, PostRepository postRepository, CommentRepository commentRepository, PrivacyService privacyService) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.privacyService = privacyService;
    }
    @GetMapping("/api/post/{id}/likes")
    public List<UserDto> getPostLikes(@PathVariable long id, Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Post post = postRepository.findById(id).orElseThrow();
        if (!privacyService.canViewPost(currentUser, post)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return post.getLikedByUsers().stream()
                .filter(user -> privacyService.canSearchUser(currentUser, user))
                .map(user -> new UserDto(user.getUsername(), user.getProfilePictureUrl()))
                .collect(Collectors.toList());
    }
    @GetMapping("/api/comment/{id}/likes")
    public List<UserDto> getCommentLikes(@PathVariable("id") long id, Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Comment comment = commentRepository.findById(id).orElseThrow();
        if (!privacyService.canViewPost(currentUser, comment.getPost())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return comment.getLikedByUsers().stream()
                .filter(user -> privacyService.canSearchUser(currentUser, user))
                .map(user -> new UserDto(user.getUsername(), user.getProfilePictureUrl()))
                .collect(Collectors.toList());
    }
}

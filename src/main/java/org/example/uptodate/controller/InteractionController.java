package org.example.uptodate.controller;

import org.example.uptodate.model.Comment;
import org.example.uptodate.model.NotificationType;
import org.example.uptodate.model.Post;
import org.example.uptodate.model.User;
import org.example.uptodate.repository.CommentRepository;
import org.example.uptodate.repository.PostRepository;
import org.example.uptodate.repository.UserRepository;
import org.example.uptodate.services.NotificationService;
import org.example.uptodate.services.PrivacyService;
import org.example.uptodate.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class InteractionController {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final PrivacyService privacyService;

    public InteractionController(PostRepository postRepository, UserRepository userRepository, CommentRepository commentRepository, UserService userService, NotificationService notificationService, PrivacyService privacyService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.userService = userService;
        this.notificationService = notificationService;
        this.privacyService = privacyService;

    }

    // 1. Post Like
    @PostMapping("/post/{id}/like")
    public String toggleLike(
            @PathVariable("id") Long postId,
            Authentication authentication,
            @RequestHeader(value = "Referer", required = false) String referer
    ) {
        Post post = postRepository.findById(postId).orElseThrow();
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (!privacyService.canInteractWithPost(currentUser, post)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (post.isLikedBy(currentUser)) {
            privacyService.removeUser(post.getLikedByUsers(), currentUser);
        } else {
            post.getLikedByUsers().add(currentUser);
            notificationService.sendNotification(currentUser, post.getUser(), NotificationType.POST_LIKE, post);
        }

        postRepository.save(post);
        return "redirect:" + (referer != null ? referer : "/feed");
    }

    // 2. Add Comment or Reply
    @PostMapping("/post/{id}/comment")
    public String addComment(
            @PathVariable("id") Long postId,
            @RequestParam("text") String text,
            @RequestParam(value = "parentId", required = false) Long parentId,
            Authentication authentication
    ) {
        if (text == null || text.trim().isEmpty()) {
            return "redirect:/post/" + postId;
        }

        Post post = postRepository.findById(postId).orElseThrow();
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (!privacyService.canInteractWithPost(currentUser, post)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Comment newComment = new Comment();
        newComment.setText(text.trim());
        newComment.setUser(currentUser);
        newComment.setPost(post);

        if (parentId != null) {
            Comment parent = commentRepository.findById(parentId).orElseThrow();
            if (parent.getPost() == null || !parent.getPost().getId().equals(post.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply target does not belong to this post");
            }
            newComment.setParentComment(parent);
            notificationService.sendNotification(currentUser, parent.getUser(), NotificationType.COMMENT_REPLY, post);
        }
        else {
            notificationService.sendNotification(currentUser, post.getUser(), NotificationType.POST_COMMENT, post);
        }

        commentRepository.save(newComment);
        return "redirect:/post/" + postId;
    }

    // 3. Comment Like (This is the one Spring Boot couldn't find!)
    @PostMapping("/comment/{id}/like")
    public String toggleCommentLike(
            @PathVariable("id") Long commentId,
            Authentication authentication,
            @RequestHeader(value = "Referer", required = false) String referer
    ) {
        Comment comment = commentRepository.findById(commentId).orElseThrow();
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (!privacyService.canInteractWithPost(currentUser, comment.getPost())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (comment.isLikedBy(currentUser)) {
            privacyService.removeUser(comment.getLikedByUsers(), currentUser);
        } else {
            comment.getLikedByUsers().add(currentUser);
            notificationService.sendNotification(currentUser, comment.getUser(), NotificationType.COMMENT_LIKE, comment.getPost());
        }

        commentRepository.save(comment);
        return "redirect:" + (referer != null ? referer : "/feed");
    }
    @PostMapping("/user/{username}/follow")
    public String toggleFollow(@PathVariable("username") String targetUsername, Authentication authentication, @RequestHeader(value = "Referer", required = false) String referer) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        User targetUser = userRepository.findByUsername(targetUsername).orElseThrow();

        if (privacyService.isSameUser(currentUser, targetUser)) {
            return "redirect:" + (referer != null ? referer : "/profile/" + targetUsername);
        }
        if (!privacyService.canFollow(currentUser, targetUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
            // If already following, unfollow
            if (targetUser.isFollowed(currentUser)) {
                privacyService.removeUser(targetUser.getFollowers(), currentUser);
                privacyService.removeUser(currentUser.getFollowing(), targetUser);
            }
            // If already requested, cancel request
            else if (targetUser.hasPendingRequestFrom(currentUser)) {
                privacyService.removeUser(targetUser.getPendingFollowers(), currentUser);
            }
            // If not following and not requested
            else {
                if (targetUser.isPrivate()) {
                    // Drop into the waiting room
                    targetUser.getPendingFollowers().add(currentUser);
                    notificationService.sendNotification(currentUser, targetUser, NotificationType.FOLLOW_REQUEST, null);

                } else {
                    // Public account - instant follow
                    targetUser.getFollowers().add(currentUser);
                    currentUser.getFollowing().add(targetUser);
                    notificationService.sendNotification(currentUser, targetUser, NotificationType.FOLLOW, null);
                }
            }
            userRepository.save(currentUser);
            userRepository.save(targetUser);
        return "redirect:" + (referer != null ? referer : "/profile/" + targetUsername);
    }
    @PostMapping("/user/{username}/accept")
    public String acceptFollowRequest(@PathVariable String username, Authentication authentication, @RequestHeader(value = "Referer", required = false) String referer) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        User requester = userRepository.findByUsername(username).orElseThrow();
        if (privacyService.isBlockedBetween(currentUser, requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (currentUser.hasPendingRequestFrom(requester)) {
            privacyService.removeUser(currentUser.getPendingFollowers(), requester);
            currentUser.getFollowers().add(requester);
            requester.getFollowing().add(currentUser);

            userRepository.save(currentUser);
            userRepository.save(requester);
            notificationService.sendNotification(currentUser, requester, NotificationType.FOLLOW, null);
        }
        return "redirect:" + (referer != null ? referer : "/settings");
    }

    @PostMapping("/user/{username}/decline")
    public String declineFollowRequest(@PathVariable String username, Authentication authentication, @RequestHeader(value = "Referer", required = false) String referer) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        User requester = userRepository.findByUsername(username).orElseThrow();

        privacyService.removeUser(currentUser.getPendingFollowers(), requester);
        userRepository.save(currentUser);

        return "redirect:" + (referer != null ? referer : "/settings");
    }

}

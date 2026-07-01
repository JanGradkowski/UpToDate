package org.example.uptodate.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Or SEQUENCE, depending on what you used for User/Post
    private Long id;

    @Column(nullable = false, length = 500)
    private String text;

    private LocalDateTime createdAt = LocalDateTime.now();
    // --- COMMENT LIKES ---
    @ManyToMany
    @JoinTable(
            name = "comment_likes",
            joinColumns = @JoinColumn(name = "comment_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> likedByUsers = new HashSet<>();

    public boolean isLikedBy(User user) {
        if (likedByUsers == null) return false;
        return likedByUsers.stream().anyMatch(likedByUser -> sameUser(likedByUser, user));
    }

    private boolean sameUser(User first, User second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.getId() != null && second.getId() != null) {
            return first.getId().equals(second.getId());
        }
        return first == second;
    }

    // --- NESTED REPLIES (Self-Referencing) ---
    // This points up to the parent comment (if this is a reply)
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Comment parentComment;

    // This points down to all the replies (if this is a parent)
    @OneToMany(mappedBy = "parentComment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC") // Replies should show oldest first, reading top to bottom!
    private List<Comment> replies = new ArrayList<>();

    // The user who wrote the comment
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The post the comment belongs to
    @ManyToOne
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // --- GETTERS AND SETTERS ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Post getPost() { return post; }
    public void setPost(Post post) { this.post = post; }

    public Set<User> getLikedByUsers() {
        return likedByUsers;
    }

    public List<Comment> getReplies() {
        return replies;
    }

    public Comment getParentComment() {
        return parentComment;
    }

    public void setLikedByUsers(Set<User> likedByUsers) {
        this.likedByUsers = likedByUsers;
    }

    public void setParentComment(Comment parentComment) {
        this.parentComment = parentComment;
    }

    public void setReplies(List<Comment> replies) {
        this.replies = replies;
    }
}

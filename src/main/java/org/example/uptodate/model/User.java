package org.example.uptodate.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.example.uptodate.model.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "app_users") // 'user' is often a reserved word in SQL, so we rename the table
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq")
    @SequenceGenerator(name = "user_seq", sequenceName = "app_user_sequence", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_followers",
            joinColumns = @JoinColumn(name = "follower_id"), // I am the follower
            inverseJoinColumns = @JoinColumn(name = "followed_id") // They are being followed
    )
    private Set<User> following = new HashSet<>();

    @ManyToMany(mappedBy = "following", fetch = FetchType.LAZY)
    private Set<User> followers = new HashSet<>();

    public boolean isFollowed(User user) {
        if (followers == null) return false;
        return followers.stream().anyMatch(follower -> sameUser(follower, user));
    }
    public boolean isFollowing(User user) {
        return following != null && following.stream().anyMatch(followed -> sameUser(followed, user));
    }

    @NotBlank
    @Column(nullable = false, unique = true, updatable = false)
    private String email;

    @Column(name = "is_two_factor_enabled")
    private boolean isTwoFactorEnabled = false;

    @Column(name = "two_factor_secret")
    private String twoFactorSecret;

    @Column(name = "is_private")
    private boolean isPrivate = false;

    // The "Waiting Room" for private accounts
    @ManyToMany
    @JoinTable(
            name = "user_pending_followers",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "pending_follower_id")
    )
    private Set<User> pendingFollowers = new HashSet<>();

    // The Block List
    @ManyToMany
    @JoinTable(
            name = "user_blocked",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "blocked_user_id")
    )
    private Set<User> blockedUsers = new HashSet<>();

    @Column(nullable = false)
    private String password; // We will encrypt this later!

    private String bio;
    private String profilePictureUrl;
    @Column (nullable = false)
    private boolean ifProfileSetUp = false;

    @ElementCollection
    @CollectionTable(name = "user_interest_weights", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "interest_name")
    @Column(name = "weight_score")
    private Map<String, Integer> interestWeights = new HashMap<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Post> posts =  new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_saved_posts",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "post_id")
    )
    private Set<Post> savedPosts = new HashSet<>();




    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getId() {
        return id;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public List<Post> getPosts() {
        return posts;
    }

    public String getBio() {
        return bio;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        return super.toString();
    }


    public void setId(Long id) {
        this.id = id;
    }

    public Set<Post> getSavedPosts() {
        return savedPosts;
    }

    public void setSavedPosts(Set<Post> savedPosts) {
        this.savedPosts = savedPosts;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setPosts(List<Post> posts) {
        this.posts = posts;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Map<String, Integer> getInterestWeights() {
        return interestWeights;
    }

    public void setInterestWeights(Map<String, Integer> interestWeights) {
        this.interestWeights = interestWeights;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setIfProfileSetUp(boolean ifProfileSetUp) {
        this.ifProfileSetUp = ifProfileSetUp;
    }
    public boolean isIfProfileSetUp() {
        return ifProfileSetUp;
    }



    public Set<User> getFollowers() {
        return followers;
    }

    public Set<User> getFollowing() {
        return following;
    }

    public void setFollowing(Set<User> following) {
        this.following = following;
    }

    public void setFollowers(Set<User> followers) {
        this.followers = followers;
    }

    public String getTwoFactorSecret() {
        return twoFactorSecret;
    }

    public boolean isTwoFactorEnabled() {
        return isTwoFactorEnabled;
    }

    public void setTwoFactorEnabled(boolean twoFactorEnabled) {
        isTwoFactorEnabled = twoFactorEnabled;
    }

    public void setTwoFactorSecret(String twoFactorSecret) {
        this.twoFactorSecret = twoFactorSecret;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public Set<User> getBlockedUsers() {
        return blockedUsers;
    }

    public Set<User> getPendingFollowers() {
        return pendingFollowers;
    }

    public void setPrivate(boolean aPrivate) {
        isPrivate = aPrivate;
    }

    public void setBlockedUsers(Set<User> blockedUsers) {
        this.blockedUsers = blockedUsers;
    }

    public void setPendingFollowers(Set<User> pendingFollowers) {
        this.pendingFollowers = pendingFollowers;
    }
    public boolean hasPendingRequestFrom(User user) {
        return pendingFollowers != null && pendingFollowers.stream().anyMatch(pendingFollower -> sameUser(pendingFollower, user));
    }

    public boolean isBlockedBy(User user) {
        return blockedUsers != null && blockedUsers.stream().anyMatch(blockedUser -> sameUser(blockedUser, user));
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

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

}

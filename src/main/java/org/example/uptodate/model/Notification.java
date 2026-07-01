    package org.example.uptodate.model;

    import jakarta.persistence.*;
    import org.example.uptodate.model. NotificationType;
    import org.hibernate.annotations.CreationTimestamp;
    import java.time.LocalDateTime;

    @Entity
    @Table(name = "notifications")
    public class Notification {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "story_id")
        private Story story;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private NotificationType type;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "receiver_id", nullable = false)
        private User receiver; // The person getting notified

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "sender_id", nullable = false)
        private User sender;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "post_id")
        private Post post;

        @Column(nullable = false)
        private boolean isRead = false;

        @CreationTimestamp
        @Column(updatable = false)
        private LocalDateTime createdAt;

        public Long getId() {
            return id;
        }

        public NotificationType getType() {
            return type;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public Post getPost() {
            return post;
        }
        public User getReceiver() {
            return receiver;
        }

        public User getSender() {
            return sender;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
        public boolean isRead() {
            return isRead;
        }
        public void setPost(Post post) {
            this.post = post;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public void setRead(boolean read) {
            isRead = read;
        }

        public void setReceiver(User receiver) {
            this.receiver = receiver;
        }

        public void setSender(User sender) {
            this.sender = sender;
        }

        public void setType(NotificationType type) {
            this.type = type;
        }

        public Story getStory() {
            return story;
        }

        public void setStory(Story story) {
            this.story = story;
        }
    }
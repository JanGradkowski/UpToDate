    package org.example.uptodate.model;

    import jakarta.persistence.*;
    import org.hibernate.annotations.CreationTimestamp;
    import java.time.LocalDateTime;

    @Entity
    @Table(name = "bookmarks", uniqueConstraints = {
            @UniqueConstraint(columnNames = {"user_id", "post_id"})
    })
    public class Bookmark {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "user_id", nullable = false)
        private User user;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "post_id", nullable = false)
        private Post post;

        @CreationTimestamp
        @Column(updatable = false)
        private LocalDateTime createdAt;
    }
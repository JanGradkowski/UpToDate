    package org.example.uptodate.repository;

    import org.example.uptodate.model.Post;
    import org.example.uptodate.model.User;
    import org.springframework.data.jpa.repository.JpaRepository;
    import org.springframework.data.jpa.repository.Query;
    import org.springframework.data.repository.query.Param;

    import java.util.List;

    public interface PostRepository extends JpaRepository<Post, Long> {
        List<Post> findAllByOrderByCreatedAtDesc();
        List<Post> findByUserOrderByCreatedAtDesc(User user);
        @Query(value = "SELECT p.* FROM posts p JOIN user_saved_posts usp ON p.id = usp.post_id WHERE usp.user_id = :userId ORDER BY p.created_at DESC FETCH FIRST 1000 ROWS ONLY", nativeQuery = true)
        List<Post> findTop1000SavedPosts(@Param("userId") Long userId);

    }
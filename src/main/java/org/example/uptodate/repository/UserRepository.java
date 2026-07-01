package org.example.uptodate.repository;

import org.example.uptodate.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findByUsernameContainingIgnoreCase(String username);
    @Query("SELECT DISTINCT s.user FROM Story s WHERE s.user IN :following AND s.expiresAt > :now")
    List<User> findUsersWithActiveStories(@Param("following") Set<User> following, @Param("now") java.time.LocalDateTime now);
    @Query("SELECT DISTINCT u FROM User me JOIN me.following u " +
            "WHERE me.id = :currentUserId " +
            "AND EXISTS (SELECT s FROM Story s WHERE s.user = u AND s.expiresAt > :now " +
            "AND NOT EXISTS (SELECT v FROM s.viewers v WHERE v.id = :currentUserId))")
    List<User> findFollowedUsersWithUnseenActiveStories(
            @Param("currentUserId") Long currentUserId,
            @Param("now") java.time.LocalDateTime now
    );
}

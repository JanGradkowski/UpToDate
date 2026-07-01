package org.example.uptodate.services;

import org.example.uptodate.model.Post;
import org.example.uptodate.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyServiceTest {

    private final PrivacyService privacyService = new PrivacyService();

    @Test
    void privateProfileIsOnlyVisibleToApprovedFollower() {
        User owner = user(1L, "owner");
        User follower = user(2L, "follower");
        User stranger = user(3L, "stranger");
        owner.setPrivate(true);
        owner.getFollowers().add(follower);

        assertThat(privacyService.canViewProfile(owner, owner)).isTrue();
        assertThat(privacyService.canViewProfile(follower, owner)).isTrue();
        assertThat(privacyService.canViewProfile(stranger, owner)).isFalse();
    }

    @Test
    void blockingPreventsSearchViewFollowAndPostInteraction() {
        User blocker = user(1L, "blocker");
        User blocked = user(2L, "blocked");
        blocker.getBlockedUsers().add(blocked);

        Post post = new Post();
        post.setUser(blocker);

        assertThat(privacyService.canSearchUser(blocked, blocker)).isFalse();
        assertThat(privacyService.canViewProfile(blocked, blocker)).isFalse();
        assertThat(privacyService.canViewPost(blocked, post)).isFalse();
        assertThat(privacyService.canFollow(blocked, blocker)).isFalse();
    }

    @Test
    void severConnectionsRemovesFollowersFollowingAndPendingRequestsBothWays() {
        User first = user(1L, "first");
        User second = user(2L, "second");
        first.getFollowers().add(second);
        first.getFollowing().add(second);
        first.getPendingFollowers().add(second);
        second.getFollowers().add(first);
        second.getFollowing().add(first);
        second.getPendingFollowers().add(first);

        privacyService.severConnections(first, second);

        assertThat(first.getFollowers()).isEmpty();
        assertThat(first.getFollowing()).isEmpty();
        assertThat(first.getPendingFollowers()).isEmpty();
        assertThat(second.getFollowers()).isEmpty();
        assertThat(second.getFollowing()).isEmpty();
        assertThat(second.getPendingFollowers()).isEmpty();
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword("password");
        return user;
    }
}

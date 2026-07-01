package org.example.uptodate.services;

import org.example.uptodate.model.Post;
import org.example.uptodate.model.User;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class PrivacyService {

    public boolean isSameUser(User first, User second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.getId() != null && second.getId() != null) {
            return first.getId().equals(second.getId());
        }
        return first == second;
    }

    public boolean routesToInboxDirectly(User sender, User recipient) {
        if (isBlockedBetween(sender, recipient)) {
            return false;
        }
        return recipient.isFollowing(sender);
    }
    public boolean containsUser(Collection<User> users, User target) {
        return users != null && users.stream().anyMatch(user -> isSameUser(user, target));
    }

    public void removeUser(Collection<User> users, User target) {
        if (users != null) {
            users.removeIf(user -> isSameUser(user, target));
        }
    }

    public boolean hasBlocked(User blocker, User blocked) {
        return blocker != null && containsUser(blocker.getBlockedUsers(), blocked);
    }

    public boolean isBlockedBetween(User first, User second) {
        return hasBlocked(first, second) || hasBlocked(second, first);
    }

    public boolean canSearchUser(User viewer, User target) {
        return isSameUser(viewer, target) || !isBlockedBetween(viewer, target);
    }

    public boolean canViewProfile(User viewer, User target) {
        if (isSameUser(viewer, target)) {
            return true;
        }
        if (isBlockedBetween(viewer, target)) {
            return false;
        }
        return !target.isPrivate() || containsUser(target.getFollowers(), viewer);
    }

    public boolean canViewPost(User viewer, Post post) {
        return post != null && canViewProfile(viewer, post.getUser());
    }

    public boolean canInteractWithPost(User actor, Post post) {
        return canViewPost(actor, post);
    }

    public boolean canFollow(User follower, User target) {
        return !isSameUser(follower, target) && !isBlockedBetween(follower, target);
    }

    public void severConnections(User first, User second) {
        removeUser(first.getFollowers(), second);
        removeUser(first.getFollowing(), second);
        removeUser(first.getPendingFollowers(), second);

        removeUser(second.getFollowers(), first);
        removeUser(second.getFollowing(), first);
        removeUser(second.getPendingFollowers(), first);
    }
}

package org.example.uptodate.repository;

import org.example.uptodate.model.Conversation;
import org.example.uptodate.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository  extends JpaRepository<Conversation,Long> {
    List<Conversation> findByParticipantsContainingOrderByUpdatedAtDesc(User participant);
    @Query("SELECT c FROM Conversation c WHERE :user1 MEMBER OF c.participants AND :user2 MEMBER OF c.participants")
    Optional<Conversation> findExistingConversation(@Param("user1") User user1, @Param("user2") User user2);

}

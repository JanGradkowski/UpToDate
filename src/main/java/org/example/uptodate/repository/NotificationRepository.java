package org.example.uptodate.repository;
import org.aspectj.weaver.ast.Not;
import org.example.uptodate.model.Notification;
import org.example.uptodate.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByReceiverOrderByCreatedAtDesc(User receiver);
    long countByReceiverAndIsReadFalse(User receiver);
    List<Notification> findByReceiverAndIsReadFalse(User receiver);
    List<Notification> findTop50ByReceiverOrderByCreatedAtDesc(User receiver);

}

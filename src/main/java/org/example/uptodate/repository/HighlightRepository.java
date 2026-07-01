package org.example.uptodate.repository;

import org.example.uptodate.model.Highlight;
import org.example.uptodate.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HighlightRepository extends JpaRepository<Highlight, Long> {
    List<Highlight> findAllByUser(User user);
}
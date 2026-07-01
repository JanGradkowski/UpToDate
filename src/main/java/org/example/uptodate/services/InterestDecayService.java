package org.example.uptodate.services;
import org.example.uptodate.model.User;
import org.example.uptodate.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class InterestDecayService {
    private final UserRepository userRepository;
    public InterestDecayService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void applyNightlyDecay() {
        System.out.println("--- RUNNING NIGHTLY ALGORITHM DECAY ---");
        List<User> users = userRepository.findAll();

        for (User user : users) {
            Map<String, Integer> weights = user.getInterestWeights();

            // Remove the interest completely if the score drops below 10.
            // Otherwise, reduce the score by 5%.
            weights.entrySet().removeIf(entry -> {
                int decayedScore = (int) (entry.getValue() * 0.95);
                entry.setValue(decayedScore);
                return decayedScore < 10;
            });
        }

        userRepository.saveAll(users);
        System.out.println("--- DECAY COMPLETE ---");
    }
}

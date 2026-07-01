package org.example.uptodate.services;
import org.example.uptodate.model.User;
import org.example.uptodate.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;

    }
    public void registerNewUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
        emailService.sendEmail(user.getEmail(), user.getUsername());
    }
    public boolean isUsernameTaken (String username) {
        return userRepository.findByUsername(username).isPresent();
    }
    public boolean isEmailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
}

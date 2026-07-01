package org.example.uptodate.controller;
import jakarta.servlet.ServletException;
import org.example.uptodate.model.User;
import org.example.uptodate.repository.UserRepository;
import org.example.uptodate.services.CustomUserDetailsService;
import org.example.uptodate.services.EmailService;
import org.example.uptodate.services.FileStorageService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Optional;

@Controller
public class SettingsController {
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final CustomUserDetailsService customUserDetailsService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final org.example.uptodate.services.VerificationService verificationService;
    public SettingsController(UserRepository userRepository,  FileStorageService fileStorageService, CustomUserDetailsService customUserDetailsService
    , org.springframework.security.crypto.password.PasswordEncoder passwordEncoder, EmailService emailService, org.example.uptodate.services.VerificationService verificationService) {
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.customUserDetailsService = customUserDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.verificationService = verificationService;
    }

    @GetMapping("/settings")
    public String showSettingsPage(Authentication authentication, Model model) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("currentUser", currentUser);
        return "settings";
    }
    @PostMapping("/settings/profile")
    public String updateProfile(Authentication authentication, @RequestParam("username") String username,
                                @RequestParam("bio") String bio,
                                @RequestParam(value = "profilePic", required = false) MultipartFile profilePic,
                                RedirectAttributes redirectAttributes) throws IOException, ServletException {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        boolean usernameChanged = !currentUser.getUsername().equals(username);
        if (usernameChanged) {
            Optional<User> existingUser = userRepository.findByUsername(username);
            if (existingUser.isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Username is already in use");
                return "redirect:/settings";
            }
            currentUser.setUsername(username);
        }
        currentUser.setBio(bio);
        if (profilePic != null &&!profilePic.isEmpty()) {
            String avatarUrl = fileStorageService.saveProfilePicture(profilePic);
            currentUser.setProfilePictureUrl(avatarUrl);
        }
        userRepository.save(currentUser);
        if (usernameChanged) {
            UserDetails updatedUser = customUserDetailsService.loadUserByUsername(username);
            Authentication newAuthentication = new UsernamePasswordAuthenticationToken(updatedUser, authentication.getCredentials(), updatedUser.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(newAuthentication);
        }
        redirectAttributes.addFlashAttribute("success", "Profile updated");
        return "redirect:/settings";
    }
    @PostMapping("/api/settings/password/request-code")
    public org.springframework.http.ResponseEntity<?> requestPasswordCode(Authentication authentication,
                    @RequestParam("oldPassword") String oldPassword)
        throws IOException, ServletException {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (!passwordEncoder.matches(oldPassword, currentUser.getPassword())) {
            return org.springframework.http.ResponseEntity.badRequest().body("Old password does not match");
        }
        String code = verificationService.generateAndStoreCode(currentUser.getEmail());
        emailService.sendPasswordChangeCode(currentUser.getEmail(), code);
        return org.springframework.http.ResponseEntity.ok().build();
    }
    @PostMapping("/settings/password/update")
    public String updatePassword(Authentication authentication,
                                 @RequestParam("oldPassword") String oldPassword,
                                 @RequestParam("newPassword") String newPassword,
                                 @RequestParam("confirmPassword") String confirmPassword,
                                 @RequestParam("verificationCode") String verificationCode,
                                 RedirectAttributes redirectAttributes) throws IOException, ServletException {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (!passwordEncoder.matches(oldPassword, currentUser.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "Old password does not match");
            return "redirect:/settings";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "New password does not match");
            return "redirect:/settings";
        }
        if (!verificationService.verifyCode(currentUser.getEmail(), verificationCode)) {
            redirectAttributes.addFlashAttribute("error", "Verification code does not match");
            return "redirect:/settings";
        }
        currentUser.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(currentUser);
        redirectAttributes.addFlashAttribute("success", "Password updated");
        return "redirect:/settings";
    }
}

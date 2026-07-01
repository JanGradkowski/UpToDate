package org.example.uptodate.controller;

import org.example.uptodate.model.User;
import org.example.uptodate.repository.UserRepository;
import org.example.uptodate.services.TwoFactorAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TwoFactorApiController {

    private final UserRepository userRepository;
    private final TwoFactorAuthService twoFactorAuthService;

    public TwoFactorApiController(UserRepository userRepository, TwoFactorAuthService twoFactorAuthService) {
        this.userRepository = userRepository;
        this.twoFactorAuthService = twoFactorAuthService;
    }

    // Triggered when they toggle the switch to ON
    @PostMapping("/api/settings/2fa/setup")
    public ResponseEntity<?> setup2FA(Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();

        // Generate the secret but DO NOT save it as active yet!
        String secret = twoFactorAuthService.generateNewSecret();
        String qrCode = twoFactorAuthService.generateQrCodeImageUri(secret, currentUser.getUsername());

        return ResponseEntity.ok(Map.of(
                "secret", secret,
                "qrCode", qrCode
        ));
    }

    // Triggered when they type the code in and hit Confirm
    @PostMapping("/api/settings/2fa/verify")
    public ResponseEntity<?> verifyAndEnable2FA(
            Authentication authentication,
            @RequestParam("code") String code,
            @RequestParam("secret") String secret) {

        if (twoFactorAuthService.isOtpValid(secret, code)) {
            User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
            currentUser.setTwoFactorEnabled(true);
            currentUser.setTwoFactorSecret(secret);
            userRepository.save(currentUser);
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.badRequest().body("Invalid verification code.");
    }

    // Triggered when they toggle the switch to OFF
    @PostMapping("/api/settings/2fa/disable")
    public ResponseEntity<?> disable2FA(Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        currentUser.setTwoFactorEnabled(false);
        currentUser.setTwoFactorSecret(null);
        userRepository.save(currentUser);
        return ResponseEntity.ok().build();
    }
}
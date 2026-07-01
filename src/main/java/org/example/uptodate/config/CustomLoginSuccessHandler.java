package org.example.uptodate.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.uptodate.model.User;
import org.example.uptodate.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;


    public CustomLoginSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        System.out.println("--- BOUNCER INTERCEPTED LOGIN ---");

        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();

        System.out.println("User logging in: " + user.getUsername());
        System.out.println("Is 2FA Enabled in DB? " + user.isTwoFactorEnabled());

        if (user.isTwoFactorEnabled()) {
            request.getSession().setAttribute("pending2faUser", user.getUsername());
            SecurityContextHolder.clearContext();
            response.sendRedirect("/verify-2fa");
        } else {
            System.out.println("Action: No 2FA. Routing to standard Setup/Feed.");
            response.sendRedirect("/setup");
        }
    }
}
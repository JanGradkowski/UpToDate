package org.example.uptodate.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.example.uptodate.model.User;
import org.example.uptodate.repository.UserRepository;
import org.example.uptodate.services.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.example.uptodate.services.TwoFactorAuthService;
import org.example.uptodate.services.CustomUserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final UserService userService;
    private final TwoFactorAuthService twoFactorAuthService; // NEW
    private final CustomUserDetailsService userDetailsService; // NEW
    private final org.example.uptodate.repository.UserRepository userRepository;

    public AuthController(UserService userService, TwoFactorAuthService twoFactorAuthService, CustomUserDetailsService userDetailsService, UserRepository userRepository) {
        this.userService = userService;
        this.twoFactorAuthService = twoFactorAuthService;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
    }

    @GetMapping("/login")
    public String showLoginForm() {
        return "login";
    }

    @GetMapping("/signup")
    public String showSignupForm(Model model) {
        model.addAttribute("user", new User());
        return "signup";
    }

    @PostMapping("/signup")
    public String registerUser(@ModelAttribute("user") User user, Model model, HttpServletRequest request) {
        String rawPassword = user.getPassword();
        if (rawPassword.length() < 8 || !rawPassword.matches(".*[A-Z].*") || !rawPassword.matches(".*\\d.*")) {
            model.addAttribute("passwordError", "Password must be at least 8 characters long, contain one capital letter, and one number.");
            return "signup";
        }
        if (userService.isUsernameTaken(user.getUsername())) {
            model.addAttribute("usernameError", "Username is already taken.");
            return "signup";
        }
        if (userService.isEmailExists(user.getEmail())) {
            model.addAttribute("emailError", "Email already exists.");
            return "signup";
        }

        userService.registerNewUser(user);
        try {
            request.login(user.getUsername(), rawPassword);
        } catch (ServletException e) {
            return "redirect:/login?registered";
        }
        return "redirect:/setup";
    }
    @GetMapping("/verify-2fa")
    public String show2faPage(HttpSession session) {
        if (session.getAttribute("pending2faUser") == null){
            return "redirect:/login";
        }
        return "verify-2fa";
    }
    @PostMapping("/verify-2fa")
    public String process2fa(@RequestParam("code") String code, HttpSession session, Model model) {
        String username = (String) session.getAttribute("pending2faUser");
        if (username == null){
            return "redirect:/login";
        }
        User user = userRepository.findByUsername(username).orElseThrow();
        if (twoFactorAuthService.isOtpValid(user.getTwoFactorSecret(), code)) {
            // SUCCESS! Hot-Swap the security context to grant them full access
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);

            // Clean up the waiting room
            session.removeAttribute("pending2faUser");

            return "redirect:/setup";
        } else {
            // FAILED! Send them back to try again
            model.addAttribute("error", "Invalid verification code.");
            return "verify-2fa";
        }
    }
}

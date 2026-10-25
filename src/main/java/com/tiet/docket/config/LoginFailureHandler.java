package com.tiet.docket.config;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.tiet.docket.repository.UserRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    // Account lockout rules: 3 failed attempts lock the account for 30 minutes
    private static final int MAX_ATTEMPTS = 3;
    private static final int LOCK_MINUTES = 30;

    private final UserRepository userRepository;

    public LoginFailureHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        // In Docket, the login form field named "username" holds the user's email
        String email = request.getParameter("username");

        if (email != null) {
            userRepository.findByEmail(email).ifPresent(user -> {
                // If the account is not already locked, count this failed attempt
                if (!(exception instanceof LockedException)) {
                    int attempts = user.getFailedLoginAttempts() + 1;
                    if (attempts >= MAX_ATTEMPTS) {
                        user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                        user.setFailedLoginAttempts(0);
                    } else {
                        user.setFailedLoginAttempts(attempts);
                    }
                    userRepository.save(user);
                }
            });
        }

        // If the account is locked, redirect to /login?locked; otherwise redirect to
        // /login?error
        if (exception instanceof LockedException) {
            setDefaultFailureUrl("/login?locked");
        } else {
            setDefaultFailureUrl("/login?error");
        }

        super.onAuthenticationFailure(request, response, exception);
    }
}

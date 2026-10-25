package com.tiet.docket.config;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.tiet.docket.repository.UserRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;

    public LoginSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
        // Default target URL is "/" which HomeController routes by user role
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        // On successful login, reset failed attempts counter and clear any lock
        userRepository.findByEmail(authentication.getName()).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        });

        super.onAuthenticationSuccess(request, response, authentication);
    }
}

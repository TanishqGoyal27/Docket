package com.tiet.docket.api;

import com.tiet.docket.admin.AdminService;
import com.tiet.docket.api.ApiDtos.LoginRequest;
import com.tiet.docket.api.ApiDtos.RegisterRequest;
import com.tiet.docket.api.ApiDtos.UserDto;
import com.tiet.docket.model.Role;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.UserRepository;
import com.tiet.docket.workflow.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * JSON login and student sign-up for the React app (UC-06.1, FR1). Logout is handled by Spring
 * Security at POST /api/auth/logout.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // QR16: three wrong passwords lock the account for 30 minutes
    private static final int MAX_ATTEMPTS = 3;
    private static final int LOCK_MINUTES = 30;

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserRepository userRepository;
    private final AdminService adminService;
    private final AuditService auditService;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          UserRepository userRepository,
                          AdminService adminService,
                          AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.userRepository = userRepository;
        this.adminService = adminService;
        this.auditService = auditService;
    }

    // Reading the token makes Spring write the XSRF-TOKEN cookie the frontend sends back
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return userRepository.findByEmail(authentication.getName())
                .map(u -> ResponseEntity.ok(UserDto.of(u)))
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest body,
                                   HttpServletRequest request, HttpServletResponse response) {
        String email = body.email() == null ? "" : body.email().trim().toLowerCase();
        String password = body.password() == null ? "" : body.password();
        try {
            Authentication auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, password));

            User user = userRepository.findByEmail(email).orElseThrow();
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);

            startSession(auth, request, response);
            return ResponseEntity.ok(UserDto.of(user));

        } catch (LockedException e) {
            return error(HttpStatus.LOCKED,
                    "Your account is locked after 3 failed attempts. Try again in 30 minutes or ask the Administrator to unlock it.");
        } catch (DisabledException e) {
            return error(HttpStatus.FORBIDDEN, "This account has been deactivated. Please contact the Administrator.");
        } catch (BadCredentialsException e) {
            boolean nowLocked = countFailedAttempt(email);
            return nowLocked
                    ? error(HttpStatus.LOCKED, "Too many failed attempts. Your account is locked for 30 minutes.")
                    : error(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }
    }

    // New students sign themselves up. The role is always STUDENT; staff accounts are
    // only created by the Administrator (SRS 2.5).
    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@RequestBody RegisterRequest body,
                                            HttpServletRequest request, HttpServletResponse response) {
        if (body.rollNumber() == null || body.rollNumber().isBlank()) {
            throw new IllegalArgumentException("Roll number is required");
        }
        if (userRepository.existsByRollNumber(body.rollNumber().trim())) {
            throw new IllegalArgumentException("An account with this roll number already exists");
        }
        User student = adminService.createUser(body.name(), body.email(), body.password(), Role.STUDENT,
                null, body.rollNumber(), body.course(), body.phone());
        auditService.log(student, "STUDENT_REGISTERED", student.getEmail() + " (roll " + student.getRollNumber() + ")");

        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(student.getEmail(), body.password()));
        startSession(auth, request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserDto.of(student));
    }

    private void startSession(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        // New session id on login prevents session fixation
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    // Returns true if this failure locked the account
    private boolean countFailedAttempt(String email) {
        return userRepository.findByEmail(email).map(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            boolean lock = attempts >= MAX_ATTEMPTS;
            if (lock) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                user.setFailedLoginAttempts(0);
            } else {
                user.setFailedLoginAttempts(attempts);
            }
            userRepository.save(user);
            return lock;
        }).orElse(false);
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}

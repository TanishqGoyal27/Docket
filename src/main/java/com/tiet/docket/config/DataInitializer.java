package com.tiet.docket.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.tiet.docket.model.Role;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.UserRepository;

@Configuration
public class DataInitializer {

    // Runs once when the app starts and creates one test user for every role
    @Bean
    CommandLineRunner seedUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            createUser(userRepository, passwordEncoder, "Test Student", "student@docket.com", Role.STUDENT, null);
            createUser(userRepository, passwordEncoder, "Test Admin Officer", "adminofficer@docket.com",
                    Role.ADMIN_OFFICER, "Administration");
            createUser(userRepository, passwordEncoder, "Test HOD", "hod@docket.com", Role.HOD, "Computer Science");
            createUser(userRepository, passwordEncoder, "Test Scholarship Officer", "scholarship@docket.com",
                    Role.SCHOLARSHIP_OFFICER, "Scholarship");
            createUser(userRepository, passwordEncoder, "Test Finance Officer", "finance@docket.com",
                    Role.FINANCE_OFFICER, "Finance");
            createUser(userRepository, passwordEncoder, "Test Accounts Officer", "accounts@docket.com",
                    Role.ACCOUNTS_OFFICER, "Accounts");
            createUser(userRepository, passwordEncoder, "Test Administrator", "admin@docket.com", Role.ADMINISTRATOR,
                    "IT");
        };
    }

    private void createUser(UserRepository userRepository, PasswordEncoder passwordEncoder,
            String name, String email, Role role, String department) {

        // Do not create the same user twice
        if (userRepository.existsByEmail(email)) {
            return;
        }

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("password123")); // stored hashed
        user.setRole(role);
        user.setDepartment(department);
        user.setActive(true);

        if (role == Role.STUDENT) {
            user.setRollNumber("102403001");
            user.setCourse("BE Computer Engineering");
            user.setPhone("9876543210");
        }

        userRepository.save(user);
    }
}
package com.tiet.docket.staff;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class StaffSecurityConfig {

    // Handles ONLY /staff/** and runs before Person 2's chain.
    // Users and password checking come from Person 2's CustomUserDetailsService.
    @Bean
    @Order(1)
    public SecurityFilterChain staffFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/staff/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/staff/portal", "/staff/login").permitAll()
                        .anyRequest().hasAnyRole(
                                "ADMIN_OFFICER", "PROFESSOR", "HOD",
                                "SCHOLARSHIP_OFFICER", "FINANCE_OFFICER", "ACCOUNTS_OFFICER"))
                .formLogin(form -> form
                        .loginPage("/staff/login")
                        .loginProcessingUrl("/staff/login")
                        .defaultSuccessUrl("/staff/dashboard", true)
                        .failureUrl("/staff/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/staff/logout")
                        .logoutSuccessUrl("/staff/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"));
        return http.build();
    }
}
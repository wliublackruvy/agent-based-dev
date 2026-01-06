package com.mingyu.app.health;

// Implements System

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Locks down /health and /actuator endpoints so only automation with the
 * configured credentials can reach them. Everything else remains unrestricted.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(ActuatorAuthProperties.class)
public class HealthSecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.logout(AbstractHttpConfigurer::disable);
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.httpBasic(Customizer.withDefaults());
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/health", "/actuator/**").hasRole("ACTUATOR")
                .anyRequest().permitAll());
        return http.build();
    }

    @Bean
    UserDetailsService actuatorUserDetailsService(
            ActuatorAuthProperties properties,
            PasswordEncoder passwordEncoder) {
        UserDetails actuatorUser = User.withUsername(properties.getUsername())
                .password(passwordEncoder.encode(properties.getPassword()))
                .roles("ACTUATOR")
                .build();
        return new InMemoryUserDetailsManager(actuatorUser);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
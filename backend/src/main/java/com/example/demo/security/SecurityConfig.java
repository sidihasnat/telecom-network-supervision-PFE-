package com.example.demo.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth

                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/metrics/latest").permitAll()

                .requestMatchers(HttpMethod.POST, "/api/metrics").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/metrics/prediction").permitAll()
                .requestMatchers("/api/label/**").permitAll()

                .requestMatchers(HttpMethod.GET, "/api/auth/users").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/auth/register").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/auth/users/**").hasRole("ADMIN")          // 🆕 تعديل
                .requestMatchers(HttpMethod.POST, "/api/auth/users/*/password").hasRole("ADMIN") // 🆕 تغيير كلمة سر آخرين
                .requestMatchers(HttpMethod.DELETE, "/api/auth/users/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.POST, "/api/auth/me/password").authenticated()

                .requestMatchers(HttpMethod.POST, "/api/alert-rules/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/alert-rules/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/alert-rules/**").hasRole("ADMIN")
                .requestMatchers("/api/protection/**").hasRole("ADMIN")
                .requestMatchers("/api/terminal/**").hasRole("ADMIN")

                .requestMatchers("/api/device/**").hasRole("ADMIN")
                .requestMatchers("/api/device-control/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.POST, "/api/playbooks/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/playbooks/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/playbooks/**").hasRole("ADMIN")
                .requestMatchers("/api/attack-sessions/**").permitAll()

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                "http://10.0.4.10",
                "http://localhost",
                "http://127.0.0.1"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

package com.aiarchitect.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
public class AuthConfig {

    @Value("${auth.enabled:false}")
    private boolean authEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwtFilter) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                if (!authEnabled) {
                    auth.anyRequest().permitAll();
                } else {
                    auth.requestMatchers("/api/auth/**", "/api/actuator/health").permitAll()
                        .anyRequest().authenticated();
                }
            });
        if (authEnabled) {
            http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }
}

@Component
class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    private final JwtService jwtService;
    JwtFilter(JwtService jwtService) { this.jwtService = jwtService; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                     FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.isValid(token)) {
                try {
                    var claims = jwtService.validateToken(token);
                    var auth   = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (Exception e) {
                    log.debug("JWT processing error: {}", e.getMessage());
                }
            }
        }
        chain.doFilter(req, res);
    }
}

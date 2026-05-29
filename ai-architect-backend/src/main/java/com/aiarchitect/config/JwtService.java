package com.aiarchitect.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    @Value("${auth.jwt-secret:ai-architect-default-secret-change-in-production-32chars}")
    private String secret;

    @Value("${auth.jwt-expiry-hours:24}")
    private int expiryHours;

    public String generateToken(String userId, String role) {
        SecretKey key    = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date      expiry = new Date(System.currentTimeMillis() + (long) expiryHours * 3600 * 1000);
        return Jwts.builder()
            .subject(userId)
            .claim("role", role)
            .claim("app", "ai-architect")
            .issuedAt(new Date())
            .expiration(expiry)
            .signWith(key)
            .compact();
    }

    public Claims validateToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build()
               .parseSignedClaims(token).getPayload();
    }

    public boolean isValid(String token) {
        try { validateToken(token); return true; }
        catch (JwtException e) { log.debug("Invalid JWT: {}", e.getMessage()); return false; }
    }
}

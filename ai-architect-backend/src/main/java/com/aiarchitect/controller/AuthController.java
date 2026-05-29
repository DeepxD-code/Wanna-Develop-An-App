package com.aiarchitect.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * AuthController
 * - Desktop mode: issues token instantly (no credentials needed)
 * - Auth mode: validates credentials before issuing token
 * - OAuth: placeholder endpoints for Google/GitHub (slot in later)
 */
@RestController
@RequestMapping("/auth")
class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);


    private final com.aiarchitect.config.JwtService jwtService;
    AuthController(com.aiarchitect.config.JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Value("${auth.enabled:false}")
    private boolean authEnabled;

    @Value("${auth.local-password:}")
    private String localPassword;

    // ── Desktop Mode: instant token ──────────────────────────────
    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> getToken(
        @RequestBody(required = false) Map<String, String> body
    ) {
        String userId = "local-user";
        String role   = "admin";

        if (authEnabled && localPassword != null && !localPassword.isBlank()) {
            String provided = body != null ? body.getOrDefault("password", "") : "";
            if (!provided.equals(localPassword)) {
                return ResponseEntity.status(401).body(Map.of(
                    "error", "Invalid credentials"
                ));
            }
        }

        String token = jwtService.generateToken(userId, role);
        log.info("Token issued for userId={}", userId);

        return ResponseEntity.ok(Map.of(
            "token",   token,
            "userId",  userId,
            "role",    role,
            "mode",    authEnabled ? "local-auth" : "desktop"
        ));
    }

    // ── Validate token ───────────────────────────────────────────
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(
        @RequestHeader(value = "Authorization", required = false) String header
    ) {
        if (header == null || !header.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("valid", false));
        }
        String token = header.substring(7);
        boolean valid = jwtService.isValid(token);
        return ResponseEntity.ok(Map.of(
            "valid",   valid,
            "enabled", authEnabled
        ));
    }

    // ── Auth status ──────────────────────────────────────────────
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
            "authEnabled", authEnabled,
            "mode",        authEnabled ? "local-auth" : "desktop-open"
        ));
    }

    // ── OAuth placeholders (slot in Google/GitHub later) ─────────
    @GetMapping("/oauth/google")
    public ResponseEntity<Map<String, String>> oauthGoogle() {
        return ResponseEntity.status(501).body(Map.of(
            "message", "Google OAuth not configured yet. Set GOOGLE_CLIENT_ID in .env"
        ));
    }

    @GetMapping("/oauth/github")
    public ResponseEntity<Map<String, String>> oauthGithub() {
        return ResponseEntity.status(501).body(Map.of(
            "message", "GitHub OAuth not configured yet. Set GITHUB_CLIENT_ID in .env"
        ));
    }
}

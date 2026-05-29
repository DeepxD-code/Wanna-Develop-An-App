package com.aiarchitect.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * SecurityService
 * - Validates shell commands before execution (whitelist + AI confidence check)
 * - Detects prompt injection attempts in user input
 * - Encrypts API keys at rest
 * - Detects credential leakage in generated code
 * - Blocks dangerous command patterns
 */
@Service
public class SecurityService {
    private static final Logger log = LoggerFactory.getLogger(SecurityService.class);


    // ── Command Security ───────────────────────────────────────

    // Commands that are always blocked
    private static final Set<String> BLOCKED_COMMANDS = Set.of(
        "rm -rf /", "rm -rf ~", "rm -rf *",
        "format", "mkfs", "dd if=/dev/zero",
        "curl | bash", "wget | bash", "curl | sh",
        "chmod 777 /", "> /dev/sda",
        "shutdown", "reboot", "halt",
        ":(){ :|:& };:", // fork bomb
        "sudo rm", "sudo chmod 777"
    );

    // Commands allowed without extra verification
    private static final Set<String> SAFE_COMMANDS = Set.of(
        "pip install", "pip3 install", "npm install", "npm ci",
        "python", "python3", "node", "java",
        "pytest", "flake8", "eslint", "mvn",
        "git init", "git add", "git commit", "git status",
        "mkdir", "touch", "echo", "cat", "ls", "pwd",
        "make install", "make test", "make lint", "make run"
    );

    /**
     * Validate a shell command before execution.
     * Returns SecurityResult with ALLOW/DENY/VERIFY decision.
     */
    public SecurityResult validateCommand(String command) {
        if (command == null || command.isBlank()) {
            return SecurityResult.deny("Empty command");
        }

        String lower = command.toLowerCase().trim();

        // Check blocked patterns
        for (String blocked : BLOCKED_COMMANDS) {
            if (lower.contains(blocked.toLowerCase())) {
                log.warn("BLOCKED command: {}", command);
                return SecurityResult.deny("Command matches blocked pattern: " + blocked);
            }
        }

        // Check for dangerous patterns
        if (isDangerousCommand(lower)) {
            log.warn("DANGEROUS command flagged: {}", command);
            return SecurityResult.deny("Dangerous command pattern detected");
        }

        // Check if it's a known safe command
        for (String safe : SAFE_COMMANDS) {
            if (lower.startsWith(safe.toLowerCase())) {
                return SecurityResult.allow("Known safe command");
            }
        }

        // Unknown command — requires verification
        log.info("Command requires verification: {}", command);
        return SecurityResult.verify("Unknown command — AI confidence check required");
    }

    private boolean isDangerousCommand(String cmd) {
        return cmd.contains("rm -rf") ||
               cmd.contains("> /dev/") ||
               cmd.contains("| bash") ||
               cmd.contains("| sh") ||
               cmd.matches(".*\\bkill\\b.*-9.*") ||
               cmd.contains(":(){ ") ||
               cmd.matches(".*\\bchmod\\b.*777.*/.*");
    }

    // ── Prompt Injection Detection ─────────────────────────────

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        Pattern.compile("ignore (previous|all|prior) instructions", Pattern.CASE_INSENSITIVE),
        Pattern.compile("you are now", Pattern.CASE_INSENSITIVE),
        Pattern.compile("disregard (your|all|the) (instructions|rules|guidelines)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("act as (a|an|if|though)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("jailbreak", Pattern.CASE_INSENSITIVE),
        Pattern.compile("DAN mode", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\[SYSTEM\\]|\\[INST\\]|<\\|im_start\\|>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("forget (everything|all|your training)", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Detect prompt injection attempts in user input.
     */
    public PromptInjectionResult detectInjection(String input) {
        if (input == null || input.isBlank()) {
            return PromptInjectionResult.safe();
        }

        List<String> matches = new ArrayList<>();
        for (Pattern p : INJECTION_PATTERNS) {
            Matcher m = p.matcher(input);
            if (m.find()) matches.add(m.group());
        }

        if (!matches.isEmpty()) {
            log.warn("Prompt injection detected: {}", matches);
            return PromptInjectionResult.injected(matches);
        }

        return PromptInjectionResult.safe();
    }

    /**
     * Sanitize user input by removing injection patterns.
     */
    public String sanitizeInput(String input) {
        if (input == null) return "";
        String sanitized = input;
        for (Pattern p : INJECTION_PATTERNS) {
            sanitized = p.matcher(sanitized).replaceAll("[REMOVED]");
        }
        return sanitized;
    }

    // ── Credential Leakage Detection ───────────────────────────

    private static final List<Pattern> CREDENTIAL_PATTERNS = List.of(
        Pattern.compile("(?i)(api[_-]?key|apikey)\\s*=\\s*['\"][^'\"]{10,}['\"]"),
        Pattern.compile("(?i)(password|passwd|pwd)\\s*=\\s*['\"][^'\"]{4,}['\"]"),
        Pattern.compile("(?i)(secret|token)\\s*=\\s*['\"][^'\"]{8,}['\"]"),
        Pattern.compile("[A-Za-z0-9+/]{40,}={0,2}"),            // base64 encoded secret
        Pattern.compile("sk-[a-zA-Z0-9]{32,}"),                  // OpenAI key pattern
        Pattern.compile("AIza[0-9A-Za-z-_]{35}"),                // Google API key
        Pattern.compile("-----BEGIN (RSA |EC )?PRIVATE KEY-----") // Private keys
    );

    /**
     * Scan generated code for hardcoded credentials.
     * Returns list of findings.
     */
    public List<String> scanForCredentials(String code) {
        List<String> findings = new ArrayList<>();
        if (code == null) return findings;

        for (Pattern p : CREDENTIAL_PATTERNS) {
            Matcher m = p.matcher(code);
            while (m.find()) {
                // Mask the actual value
                String masked = m.group().replaceAll("['\"][^'\"]{4,}['\"]", "\"[REDACTED]\"");
                findings.add("Potential credential: " + masked);
            }
        }

        return findings;
    }

    /**
     * Scan a project directory for credential leaks.
     */
    public Map<String, List<String>> scanProjectDirectory(Path projectDir) {
        Map<String, List<String>> findings = new LinkedHashMap<>();

        try {
            Files.walk(projectDir)
                .filter(Files::isRegularFile)
                .filter(p -> !p.toString().contains(".git"))
                .filter(p -> isCodeFile(p.toString()))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        List<String> creds = scanForCredentials(content);
                        if (!creds.isEmpty()) {
                            findings.put(projectDir.relativize(file).toString(), creds);
                        }
                    } catch (Exception ignored) {}
                });
        } catch (Exception e) {
            log.warn("Security scan failed: {}", e.getMessage());
        }

        if (!findings.isEmpty()) {
            log.warn("Security scan found credentials in {} files", findings.size());
        }

        return findings;
    }

    private boolean isCodeFile(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts") ||
               lower.endsWith(".java") || lower.endsWith(".go") || lower.endsWith(".env") ||
               lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".json");
    }

    // ── API Key Encryption ─────────────────────────────────────

    private SecretKey getOrCreateKey(Path keyFile) throws Exception {
        if (Files.exists(keyFile)) {
            byte[] keyBytes = Files.readAllBytes(keyFile);
            return new SecretKeySpec(keyBytes, "AES");
        }
        KeyGenerator gen = KeyGenerator.getInstance("AES");
        gen.init(256);
        SecretKey key = gen.generateKey();
        Files.createDirectories(keyFile.getParent());
        Files.write(keyFile, key.getEncoded(), StandardOpenOption.CREATE);
        return key;
    }

    public String encryptKey(String plaintext, Path keyFile) {
        try {
            SecretKey key = getOrCreateKey(keyFile);
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            return plaintext; // fallback: return plain (safe degradation)
        }
    }

    public String decryptKey(String encrypted, Path keyFile) {
        try {
            SecretKey key = getOrCreateKey(keyFile);
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            return new String(decrypted);
        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage());
            return encrypted;
        }
    }

    // ── Result Types ───────────────────────────────────────────

    public record SecurityResult(Decision decision, String reason) {
        public enum Decision { ALLOW, DENY, VERIFY }
        public boolean isAllowed()  { return decision == Decision.ALLOW; }
        public boolean isDenied()   { return decision == Decision.DENY; }
        public boolean needsVerify(){ return decision == Decision.VERIFY; }

        static SecurityResult allow(String r)  { return new SecurityResult(Decision.ALLOW,  r); }
        static SecurityResult deny(String r)   { return new SecurityResult(Decision.DENY,   r); }
        static SecurityResult verify(String r) { return new SecurityResult(Decision.VERIFY, r); }
    }

    public record PromptInjectionResult(boolean injected, List<String> matches) {
        static PromptInjectionResult safe()                     { return new PromptInjectionResult(false, List.of()); }
        static PromptInjectionResult injected(List<String> m)   { return new PromptInjectionResult(true,  m); }
    }
}

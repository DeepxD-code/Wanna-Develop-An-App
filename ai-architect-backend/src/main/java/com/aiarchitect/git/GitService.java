package com.aiarchitect.git;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * GitService
 * Initializes a Git repository for the generated project.
 * Creates one commit per phase — full audit trail.
 *
 * Uses ProcessBuilder (system git) — no external Java lib needed.
 * Falls back gracefully if git is not installed.
 */
@Service
public class GitService {
    private static final Logger log = LoggerFactory.getLogger(GitService.class);


    private static final String GIT_USER_NAME  = "AI Architect";
    private static final String GIT_USER_EMAIL = "ai-architect@local";

    // Phase commit messages — one per phase
    private static final Map<Integer, String> PHASE_COMMITS = Map.ofEntries(
        Map.entry(1,  "feat: auto-ideation — project concept selected"),
        Map.entry(2,  "docs: auto-ranking — winner analysis complete"),
        Map.entry(3,  "docs: research — market analysis and risks"),
        Map.entry(4,  "docs: architecture — system design and modules"),
        Map.entry(5,  "docs: implementation plan — 6-phase roadmap"),
        Map.entry(6,  "docs: api-integration — prompt templates and context design"),
        Map.entry(7,  "feat: code-generation — production source code"),
        Map.entry(8,  "fix: error-checking — 3-pass audit applied"),
        Map.entry(9,  "test: stress-testing — 8 resilience scenarios"),
        Map.entry(10, "docs: extensibility — quarterly roadmap and improvements"),
        Map.entry(11, "docs: final-report — production readiness assessment")
    );

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Initialize git repo in project directory.
     * Creates initial commit with project structure.
     */
    public boolean initRepo(Path projectDir) {
        if (!isGitAvailable()) {
            log.warn("Git not found — skipping git initialization");
            return false;
        }

        try {
            // git init
            run(projectDir, "git", "init");

            // Configure user (local only)
            run(projectDir, "git", "config", "user.name",  GIT_USER_NAME);
            run(projectDir, "git", "config", "user.email", GIT_USER_EMAIL);

            // Write .gitignore
            writeGitIgnore(projectDir);

            // Initial commit
            run(projectDir, "git", "add", ".");
            run(projectDir, "git", "commit", "-m", "chore: initialize project structure");

            log.info("Git repo initialized: {}", projectDir);
            return true;

        } catch (Exception e) {
            log.warn("Git init failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Commit all changes after a phase completes.
     */
    public boolean commitPhase(Path projectDir, int phaseId) {
        if (!isGitRepo(projectDir)) return false;

        String message = PHASE_COMMITS.getOrDefault(phaseId,
            "feat: phase-" + phaseId + " complete");

        try {
            run(projectDir, "git", "add", ".");

            // Check if there's anything to commit
            int status = runWithStatus(projectDir, "git", "diff", "--cached", "--quiet");
            if (status == 0) {
                log.debug("Phase {} — nothing to commit", phaseId);
                return true; // nothing changed, that's fine
            }

            run(projectDir, "git", "commit", "-m", message);
            log.info("Phase {} committed: {}", phaseId, message);
            return true;

        } catch (Exception e) {
            log.warn("Git commit for phase {} failed: {}", phaseId, e.getMessage());
            return false;
        }
    }

    /**
     * Get git log as string (for display).
     */
    public String getLog(Path projectDir) {
        if (!isGitRepo(projectDir)) return "Git not initialized";
        try {
            return runCapture(projectDir, "git", "log",
                "--oneline", "--graph", "--all");
        } catch (Exception e) {
            return "Could not read git log: " + e.getMessage();
        }
    }

    /**
     * Get list of all commits with their hashes.
     */
    public List<Map<String, String>> getCommits(Path projectDir) {
        List<Map<String, String>> commits = new ArrayList<>();
        if (!isGitRepo(projectDir)) return commits;

        try {
            String log = runCapture(projectDir, "git", "log",
                "--pretty=format:%H|%s|%ai");
            for (String line : log.split("\n")) {
                String[] parts = line.split("\\|", 3);
                if (parts.length == 3) {
                    commits.add(Map.of(
                        "hash",    parts[0].substring(0, Math.min(7, parts[0].length())),
                        "message", parts[1],
                        "date",    parts[2]
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Could not read commits: {}", e.getMessage());
        }

        return commits;
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private void writeGitIgnore(Path projectDir) throws IOException {
        String gitignore = """
            # Python
            __pycache__/
            *.py[cod]
            *.egg-info/
            .venv/
            venv/
            dist/
            build/
            *.egg
            
            # Node
            node_modules/
            .npm
            *.log
            
            # Java
            target/
            *.class
            *.jar
            
            # Secrets
            .env
            *.pem
            *.key
            secrets/
            
            # IDE
            .idea/
            .vscode/
            *.iml
            
            # OS
            .DS_Store
            Thumbs.db
            
            # AI Architect
            .ai-architect.json
            """;
        Files.writeString(projectDir.resolve(".gitignore"), gitignore);
    }

    private boolean isGitAvailable() {
        try {
            Process p = new ProcessBuilder("git", "--version")
                .redirectErrorStream(true)
                .start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isGitRepo(Path dir) {
        return Files.exists(dir.resolve(".git"));
    }

    private void run(Path dir, String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd)
            .directory(dir.toFile())
            .redirectErrorStream(true)
            .start();
        int exit = p.waitFor();
        if (exit != 0) {
            String out = new String(p.getInputStream().readAllBytes());
            throw new IOException("Command failed (exit " + exit + "): " + String.join(" ", cmd) + "\n" + out);
        }
    }

    private int runWithStatus(Path dir, String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd)
            .directory(dir.toFile())
            .redirectErrorStream(true)
            .start();
        return p.waitFor();
    }

    private String runCapture(Path dir, String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd)
            .directory(dir.toFile())
            .redirectErrorStream(true)
            .start();
        p.waitFor();
        return new String(p.getInputStream().readAllBytes()).trim();
    }
}

package com.aiarchitect.output;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aiarchitect.parser.CodeParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ProjectWriter
 * Writes AI-generated content to real files on disk.
 *
 * Output structure:
 *   ~/ai-architect-projects/
 *     └── {projectName}_{timestamp}/
 *         ├── src/           ← generated code (Phase 7)
 *         ├── docs/          ← documentation (Phase 11)
 *         ├── .github/workflows/ ← CI/CD (Phase 5)
 *         ├── tests/         ← test files
 *         ├── phases/        ← raw phase outputs (audit trail)
 *         │   ├── phase_01_ideation.md
 *         │   ├── phase_02_ranking.md
 *         │   └── ...
 *         └── README.md
 */
@Service
public class ProjectWriter {
    private static final Logger log = LoggerFactory.getLogger(ProjectWriter.class);


    private final CodeParser codeParser;

    @Value("${app.projects-dir:${user.home}/ai-architect-projects}")
    private String projectsBaseDir;

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Initialize project directory for a session.
     * Called when session starts — creates folder structure immediately.
     */
    public Path initProjectDir(String sessionId, String projectName) throws IOException {
        String safeName   = sanitizeName(projectName);
        String timestamp  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String dirName    = safeName + "_" + timestamp;

        Path projectDir = Path.of(projectsBaseDir, dirName);

        // Create standard folder structure
        Files.createDirectories(projectDir.resolve("src"));
        Files.createDirectories(projectDir.resolve("tests"));
        Files.createDirectories(projectDir.resolve("docs"));
        Files.createDirectories(projectDir.resolve("phases"));
        Files.createDirectories(projectDir.resolve(".github/workflows"));
        Files.createDirectories(projectDir.resolve("config"));
        Files.createDirectories(projectDir.resolve("scripts"));

        // Write session metadata
        writeFile(projectDir.resolve(".ai-architect.json"),
            String.format("""
                {
                  "sessionId": "%s",
                  "projectName": "%s",
                  "createdAt": "%s",
                  "status": "in-progress"
                }
                """, sessionId, safeName, LocalDateTime.now()));

        log.info("Project directory initialized: {}", projectDir);
        return projectDir;
    }

    /**
     * Write raw phase output to phases/ folder (audit trail).
     * Called after EVERY phase completes.
     */
    public void writePhaseOutput(Path projectDir, int phaseId, String phaseName, String output)
            throws IOException {
        String filename = String.format("phase_%02d_%s.md", phaseId,
            phaseName.toLowerCase().replace(" ", "_"));
        Path phaseFile = projectDir.resolve("phases").resolve(filename);

        String content = String.format("# Phase %d: %s\n\n%s\n", phaseId, phaseName, output);
        writeFile(phaseFile, content);
        log.debug("Phase {} output written: {}", phaseId, phaseFile);
    }

    /**
     * Write generated code files to disk (Phase 7).
     * Parses AI output and creates real files.
     */
    public List<String> writeCodeFiles(Path projectDir, String codeOutput) throws IOException {
        Map<String, String> files = codeParser.parseCodeFiles(codeOutput);
        List<String> written = new ArrayList<>();

        for (Map.Entry<String, String> entry : files.entrySet()) {
            String filePath = entry.getKey();
            String content  = entry.getValue();

            // Place in src/ if no explicit path
            if (!filePath.contains("/")) {
                filePath = "src/" + filePath;
            }

            Path target = projectDir.resolve(filePath);
            Files.createDirectories(target.getParent());
            writeFile(target, content);
            written.add(filePath);
            log.info("Code file written: {}", filePath);
        }

        // If no files parsed, write raw output as fallback
        if (written.isEmpty()) {
            Path fallback = projectDir.resolve("src/generated_code.md");
            writeFile(fallback, codeOutput);
            written.add("src/generated_code.md");
            log.warn("No structured code files detected — wrote raw output");
        }

        return written;
    }

    /**
     * Write documentation files (Phase 11).
     */
    public List<String> writeDocFiles(Path projectDir, String docOutput) throws IOException {
        Map<String, String> docs = codeParser.parseDocFiles(docOutput);
        List<String> written = new ArrayList<>();

        for (Map.Entry<String, String> entry : docs.entrySet()) {
            Path target = projectDir.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            writeFile(target, entry.getValue());
            written.add(entry.getKey());
        }

        return written;
    }

    /**
     * Write architecture files (Phase 4).
     */
    public List<String> writeArchitectureFiles(Path projectDir, String archOutput) throws IOException {
        Map<String, String> archFiles = codeParser.parseArchitectureFiles(archOutput);
        List<String> written = new ArrayList<>();

        for (Map.Entry<String, String> entry : archFiles.entrySet()) {
            Path target = projectDir.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            writeFile(target, entry.getValue());
            written.add(entry.getKey());
        }

        return written;
    }

    /**
     * Write CI/CD workflow files (Phase 5 - implementation plan).
     */
    public void writeCiCdFiles(Path projectDir, String planOutput) throws IOException {
        Path ciDir = projectDir.resolve(".github/workflows");

        // Extract YAML blocks that look like CI/CD configs
        java.util.regex.Pattern yamlBlock = java.util.regex.Pattern.compile(
            "```ya?ml\\n([\\s\\S]*?)```", java.util.regex.Pattern.MULTILINE);
        java.util.regex.Matcher m = yamlBlock.matcher(planOutput);

        int count = 0;
        while (m.find()) {
            String yaml = m.group(1).trim();
            if (yaml.contains("on:") || yaml.contains("jobs:")) { // looks like GitHub Actions
                String fname = switch (count) {
                    case 0 -> "ci.yml";
                    case 1 -> "test.yml";
                    case 2 -> "deploy.yml";
                    default -> "workflow_" + count + ".yml";
                };
                writeFile(ciDir.resolve(fname), yaml);
                log.info("CI/CD file written: .github/workflows/{}", fname);
                count++;
            }
        }
    }


    /**
     * Write a LICENSE file to the project directory.
     * Defaults to MIT — most permissive for generated projects.
     */
    public void writeLicense(Path projectDir, String projectName) throws IOException {
        int year = java.time.LocalDate.now().getYear();
        String mit = String.format("""
            MIT License

            Copyright (c) %d %s

            Permission is hereby granted, free of charge, to any person obtaining a copy
            of this software and associated documentation files (the "Software"), to deal
            in the Software without restriction, including without limitation the rights
            to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
            copies of the Software, and to permit persons to whom the Software is
            furnished to do so, subject to the following conditions:

            The above copyright notice and this permission notice shall be included in all
            copies or substantial portions of the Software.

            THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
            IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
            FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
            AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
            LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
            OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
            SOFTWARE.
            """, year, sanitizeName(projectName));
        writeFile(projectDir.resolve("LICENSE"), mit);
        log.info("LICENSE (MIT) written");
    }

    /**
     * Write a standard .env.example template.
     */
    public void writeEnvExample(Path projectDir, String codeOutput) throws IOException {
        // Extract env vars mentioned in code
        java.util.regex.Pattern envPattern = java.util.regex.Pattern.compile(
            "(?:os\\.getenv|process\\.env|System\\.getenv|os\\.environ)\\[?['\"]([A-Z_][A-Z0-9_]+)['\"]");
        java.util.regex.Matcher m = envPattern.matcher(codeOutput);

        Set<String> envVars = new LinkedHashSet<>();
        while (m.find()) envVars.add(m.group(1));

        if (!envVars.isEmpty()) {
            StringBuilder envContent = new StringBuilder("# Environment Variables\n# Copy to .env and fill in values\n\n");
            envVars.forEach(v -> envContent.append(v).append("=YOUR_").append(v).append("_HERE\n"));
            writeFile(projectDir.resolve(".env.example"), envContent.toString());
            log.info(".env.example written with {} variables", envVars.size());
        }
    }

    /**
     * Write Makefile with common tasks.
     */
    public void writeMakefile(Path projectDir, String projectName) throws IOException {
        String makefile = String.format("""
            .PHONY: install test lint run clean
            
            PROJECT = %s
            
            install:
            \tpip install -r requirements.txt 2>/dev/null || npm install 2>/dev/null || true
            
            test:
            \tpython -m pytest tests/ -v 2>/dev/null || npm test 2>/dev/null || true
            
            lint:
            \tpython -m flake8 src/ 2>/dev/null || npx eslint src/ 2>/dev/null || true
            
            run:
            \tpython src/main.py 2>/dev/null || node src/index.js 2>/dev/null || true
            
            clean:
            \tfind . -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null || true
            \trm -rf node_modules/.cache 2>/dev/null || true
            """, sanitizeName(projectName));
        writeFile(projectDir.resolve("Makefile"), makefile);
    }

    /**
     * Finalize project — update metadata, create summary.
     */
    public void finalizeProject(Path projectDir, String projectName, Map<String, Object> session)
            throws IOException {
        // Update metadata
        writeFile(projectDir.resolve(".ai-architect.json"),
            String.format("""
                {
                  "projectName": "%s",
                  "status": "complete",
                  "completedAt": "%s",
                  "phases": 11
                }
                """, sanitizeName(projectName), LocalDateTime.now()));

        // Write project summary
        String summary = buildProjectSummary(projectName, projectDir);
        writeFile(projectDir.resolve("PROJECT_SUMMARY.md"), summary);
        log.info("Project finalized: {}", projectDir);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private String sanitizeName(String name) {
        if (name == null || name.isBlank()) return "project";
        return name.toLowerCase()
                   .replaceAll("[^a-z0-9_\\-]", "_")
                   .replaceAll("_+", "_")
                   .replaceAll("^_|_$", "")
                   .substring(0, Math.min(name.length(), 40));
    }

    public ProjectWriter(
        CodeParser codeParser
    ) {
        this.codeParser = codeParser;
    }

    private String buildProjectSummary(String projectName, Path projectDir) throws IOException {
        long fileCount = Files.walk(projectDir)
            .filter(Files::isRegularFile)
            .filter(p -> !p.toString().contains(".ai-architect"))
            .count();

        return String.format("""
            # %s — Project Summary
            
            Generated by AI Architect on %s
            
            ## Files Generated
            Total files: %d
            
            ## Structure
            ```
            %s/
            ├── src/          Code files
            ├── tests/        Test files
            ├── docs/         Documentation
            ├── phases/       Phase audit trail (raw AI outputs)
            ├── .github/      CI/CD workflows
            ├── .env.example  Environment template
            ├── Makefile      Common tasks
            └── README.md     Project documentation
            ```
            
            ## Quick Start
            1. `cp .env.example .env` and fill in your values
            2. `make install` to install dependencies
            3. `make run` to start the application
            4. `make test` to run tests
            """,
            projectName,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
            fileCount,
            sanitizeName(projectName)
        );
    }

    public Path getProjectsBaseDir() {
        return Path.of(projectsBaseDir);
    }
}

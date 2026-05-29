package com.aiarchitect.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.*;

/**
 * CodeParser
 * Parses AI-generated phase outputs into real file structures.
 *
 * Handles these AI output formats:
 *   Format A: ### filename.py\n```python\n...code...\n```
 *   Format B: **File: src/main.py**\n```python\n...code...\n```
 *   Format C: // File: main.py\n...code...
 *   Format D: # main.py\n```python\n...code...\n```
 */
@Component
public class CodeParser {
    private static final Logger log = LoggerFactory.getLogger(CodeParser.class);


    // Patterns to detect file headers in AI output
    private static final List<Pattern> FILE_HEADER_PATTERNS = List.of(
        // ### filename.ext or #### filename.ext
        Pattern.compile("^#{1,4}\\s+[`']?([\\w./\\-]+\\.\\w+)[`']?\\s*$", Pattern.MULTILINE),
        // **File: path/to/file.ext** or **`file.ext`**
        Pattern.compile("^\\*{1,2}(?:File:|filename:)?\\s*[`']?([\\w./\\-]+\\.\\w+)[`']?\\*{1,2}\\s*$", Pattern.MULTILINE),
        // // File: path/to/file.ext  or  # File: file.ext
        Pattern.compile("^[/#]+\\s*(?:File:|filename:)?\\s*([\\w./\\-]+\\.\\w+)\\s*$", Pattern.MULTILINE),
        // --- path/to/file.ext ---
        Pattern.compile("^---\\s*([\\w./\\-]+\\.\\w+)\\s*---\\s*$", Pattern.MULTILINE),
        // `path/to/file.ext` (standalone backtick filename)
        Pattern.compile("^`([\\w./\\-]+\\.\\w+)`\\s*$", Pattern.MULTILINE)
    );

    // Code fence pattern
    private static final Pattern CODE_FENCE =
        Pattern.compile("```(?:\\w+)?\\n([\\s\\S]*?)```", Pattern.MULTILINE);

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Parse AI output from phase 7 into a map of filename → file content.
     */
    public Map<String, String> parseCodeFiles(String aiOutput) {
        Map<String, String> files = new LinkedHashMap<>();
        if (aiOutput == null || aiOutput.isBlank()) return files;

        // Try structured extraction first
        files = extractByFileHeaders(aiOutput);

        // If that found nothing, try extracting all code blocks generically
        if (files.isEmpty()) {
            files = extractByCodeFences(aiOutput);
        }

        log.info("CodeParser extracted {} files from AI output", files.size());
        files.forEach((name, content) ->
            log.debug("  → {}: {} chars", name, content.length()));

        return files;
    }

    /**
     * Parse documentation output into separate doc files.
     */
    public Map<String, String> parseDocFiles(String aiOutput) {
        Map<String, String> docs = new LinkedHashMap<>();
        if (aiOutput == null || aiOutput.isBlank()) return docs;

        // Extract README
        extractSection(aiOutput, "README", "README.md").ifPresent(v -> docs.put("README.md", v));

        // Extract API docs
        extractSection(aiOutput, "API", "docs/API.md").ifPresent(v -> docs.put("docs/API.md", v));

        // Extract architecture
        extractSection(aiOutput, "ARCHITECTURE", "docs/ARCHITECTURE.md")
            .ifPresent(v -> docs.put("docs/ARCHITECTURE.md", v));

        // Extract setup guide
        extractSection(aiOutput, "SETUP|DEPLOYMENT|INSTALLATION", "docs/SETUP.md")
            .ifPresent(v -> docs.put("docs/SETUP.md", v));

        // If nothing matched, save full output as a single doc
        if (docs.isEmpty()) {
            docs.put("docs/REPORT.md", aiOutput);
        }

        return docs;
    }

    /**
     * Parse architecture output into diagram files.
     */
    public Map<String, String> parseArchitectureFiles(String aiOutput) {
        Map<String, String> arch = new LinkedHashMap<>();
        if (aiOutput == null || aiOutput.isBlank()) return arch;

        arch.put("docs/ARCHITECTURE.md", aiOutput);

        // Extract ASCII diagrams into separate file
        Pattern asciiDiagram = Pattern.compile("```(?:ascii|text|plain)?\\n([\\s\\S]*?(?:[+|─┌┐└┘├┤╔╗╚╝║═])[\\s\\S]*?)```");
        Matcher m = asciiDiagram.matcher(aiOutput);
        StringBuilder diagrams = new StringBuilder("# Architecture Diagrams\n\n");
        int count = 0;
        while (m.find()) {
            diagrams.append("## Diagram ").append(++count).append("\n```\n")
                    .append(m.group(1)).append("\n```\n\n");
        }
        if (count > 0) arch.put("docs/DIAGRAMS.md", diagrams.toString());

        return arch;
    }

    // ── Extraction Helpers ─────────────────────────────────────────────────────

    private Map<String, String> extractByFileHeaders(String text) {
        Map<String, String> files = new LinkedHashMap<>();

        // Split text into lines for analysis
        String[] lines = text.split("\n");
        String currentFile = null;
        StringBuilder currentContent = new StringBuilder();
        boolean inCodeFence = false;
        String fenceType = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Check if this line is a file header
            String detectedFile = detectFileHeader(line);

            if (detectedFile != null && !inCodeFence) {
                // Save previous file
                if (currentFile != null && !currentContent.isEmpty()) {
                    files.put(normalizeFilePath(currentFile), currentContent.toString().trim());
                }
                currentFile    = detectedFile;
                currentContent = new StringBuilder();
                continue;
            }

            // Track code fence state
            if (line.startsWith("```")) {
                if (!inCodeFence) {
                    inCodeFence = true;
                    fenceType   = line.substring(3).trim();
                    continue; // skip the opening fence line
                } else {
                    inCodeFence = false;
                    continue; // skip the closing fence line
                }
            }

            // Accumulate content
            if (currentFile != null) {
                currentContent.append(line).append("\n");
            }
        }

        // Save last file
        if (currentFile != null && !currentContent.isEmpty()) {
            files.put(normalizeFilePath(currentFile), currentContent.toString().trim());
        }

        return files;
    }

    private Map<String, String> extractByCodeFences(String text) {
        Map<String, String> files = new LinkedHashMap<>();
        Matcher m = CODE_FENCE.matcher(text);
        int count = 0;

        while (m.find()) {
            String code = m.group(1).trim();
            if (code.length() < 20) continue; // skip tiny snippets

            // Try to detect language from fence header for extension
            String beforeFence = text.substring(Math.max(0, m.start() - 200), m.start());
            String lang        = detectLanguageFromContext(beforeFence, text.substring(m.start(), m.start() + 20));
            String filename    = "src/generated_" + (++count) + extensionForLang(lang);

            files.put(filename, code);
        }

        return files;
    }

    private String detectFileHeader(String line) {
        line = line.trim();
        if (line.isEmpty()) return null;

        for (Pattern p : FILE_HEADER_PATTERNS) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                String fname = m.group(1);
                // Sanity check — must look like a real filename
                if (fname.contains(".") && fname.length() < 100 && !fname.contains(" ")) {
                    return fname;
                }
            }
        }
        return null;
    }

    private Optional<String> extractSection(String text, String sectionPattern, String defaultKey) {
        Pattern p = Pattern.compile(
            "(?:^|\\n)#+\\s*(?:" + sectionPattern + ")[^\\n]*\\n([\\s\\S]*?)(?=\\n#+\\s|$)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(text);
        if (m.find()) return Optional.of(m.group(1).trim());
        return Optional.empty();
    }

    private String detectLanguageFromContext(String before, String fenceStart) {
        String lower = (before + fenceStart).toLowerCase();
        if (lower.contains("python") || lower.contains(".py")) return "python";
        if (lower.contains("javascript") || lower.contains(".js")) return "javascript";
        if (lower.contains("typescript") || lower.contains(".ts")) return "typescript";
        if (lower.contains("java") && !lower.contains("javascript")) return "java";
        if (lower.contains("go\n") || lower.contains(".go")) return "go";
        if (lower.contains("rust") || lower.contains(".rs")) return "rust";
        if (lower.contains("yaml") || lower.contains(".yml")) return "yaml";
        if (lower.contains("json")) return "json";
        if (lower.contains("bash") || lower.contains("shell")) return "bash";
        return "text";
    }

    private String extensionForLang(String lang) {
        return switch (lang) {
            case "python"     -> ".py";
            case "javascript" -> ".js";
            case "typescript" -> ".ts";
            case "java"       -> ".java";
            case "go"         -> ".go";
            case "rust"       -> ".rs";
            case "yaml"       -> ".yml";
            case "json"       -> ".json";
            case "bash"       -> ".sh";
            default           -> ".txt";
        };
    }

    private String normalizeFilePath(String path) {
        // Remove leading slashes, normalize separators
        return path.replaceAll("^[/\\\\]+", "")
                   .replace("\\", "/")
                   .replaceAll("//+", "/");
    }
}

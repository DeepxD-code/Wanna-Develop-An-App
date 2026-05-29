package com.aiarchitect.output;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/**
 * ZipExporter
 * Packages a project directory into a downloadable ZIP file.
 * Excludes: .git internals, __pycache__, node_modules, .env
 */
@Service
public class ZipExporter {
    private static final Logger log = LoggerFactory.getLogger(ZipExporter.class);


    private static final java.util.Set<String> EXCLUDE_DIRS = java.util.Set.of(
        "node_modules", "__pycache__", ".pytest_cache", "target", "build", "dist"
    );

    private static final java.util.Set<String> EXCLUDE_FILES = java.util.Set.of(
        ".env", ".DS_Store", "Thumbs.db"
    );

    /**
     * Create a ZIP of the project directory.
     * Returns path to the ZIP file (sibling of projectDir).
     */
    public Path zipProject(Path projectDir) throws IOException {
        String zipName = projectDir.getFileName().toString() + ".zip";
        Path zipPath   = projectDir.getParent().resolve(zipName);

        log.info("Creating ZIP: {}", zipPath);

        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(zipPath.toFile())))) {

            zos.setLevel(Deflater.BEST_COMPRESSION);

            Files.walk(projectDir)
                .filter(path -> !shouldExclude(path, projectDir))
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        String entryName = projectDir.relativize(path).toString()
                            .replace("\\", "/"); // normalize Windows paths
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(path, zos);
                        zos.closeEntry();
                        log.debug("  zipped: {}", entryName);
                    } catch (IOException e) {
                        log.warn("Failed to zip file {}: {}", path, e.getMessage());
                    }
                });
        }

        long sizeMb = Files.size(zipPath) / 1024 / 1024;
        log.info("ZIP created: {} ({}MB)", zipPath, sizeMb);
        return zipPath;
    }

    /**
     * Create a ZIP from a byte stream for direct HTTP response.
     */
    public byte[] zipProjectToBytes(Path projectDir) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(baos))) {
            zos.setLevel(Deflater.BEST_COMPRESSION);

            Files.walk(projectDir)
                .filter(path -> !shouldExclude(path, projectDir))
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        String entryName = projectDir.relativize(path).toString()
                            .replace("\\", "/");
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        log.warn("Failed to zip {}: {}", path, e.getMessage());
                    }
                });
        }

        return baos.toByteArray();
    }

    // ── Exclusion Logic ────────────────────────────────────────────────────────

    private boolean shouldExclude(Path path, Path base) {
        String name = path.getFileName().toString();

        // Exclude specific files
        if (EXCLUDE_FILES.contains(name)) return true;

        // Exclude hidden files (except .env.example, .github, .gitignore)
        if (name.startsWith(".") &&
            !name.equals(".env.example") &&
            !name.equals(".gitignore") &&
            !name.equals(".github")) return true;

        // Exclude specific directories
        for (Path part : path) {
            if (EXCLUDE_DIRS.contains(part.toString())) return true;
        }

        return false;
    }
}

package com.aiarchitect.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path dotenv = findDotenv();
        if (dotenv == null) return;

        try {
            Properties props = new Properties();
            List<String> lines = Files.readAllLines(dotenv);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                if (val.startsWith("\"") && val.endsWith("\"")) val = val.substring(1, val.length() - 1);
                else if (val.startsWith("'") && val.endsWith("'")) val = val.substring(1, val.length() - 1);
                props.setProperty(key, val);
            }
            if (!props.isEmpty()) {
                environment.getPropertySources().addAfter("systemEnvironment", new PropertiesPropertySource("dotenv", props));
            }
        } catch (IOException e) {
            // .env is optional
        }
    }

    private Path findDotenv() {
        Path dir = Paths.get("").toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>(List.of(
            dir.resolve(".env"),
            dir.getParent().resolve(".env")
        ));
        Path backendDir = dir.resolve("ai-architect-backend");
        if (Files.isDirectory(backendDir)) candidates.add(backendDir.resolve(".env"));
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }
}

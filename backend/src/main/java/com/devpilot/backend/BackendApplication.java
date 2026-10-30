package com.devpilot.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

@SpringBootApplication
public class BackendApplication {

    private static final Logger log = LoggerFactory.getLogger(BackendApplication.class);

    public static void main(String[] args) {
        loadDotEnvIfPresent();
        SpringApplication.run(BackendApplication.class, args);
    }

    private static void loadDotEnvIfPresent() {
        File[] candidateLocations = new File[]{
                new File(".env"),
                new File("../.env"),
                new File("../../.env")
        };

        for (File envFile : candidateLocations) {
            if (envFile.exists() && envFile.isFile()) {
                try {
                    log.info("Loading environment variables from {}", envFile.getCanonicalPath());
                    List<String> lines = Files.readAllLines(envFile.toPath());
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                            continue;
                        }
                        int eqIndex = trimmed.indexOf('=');
                        if (eqIndex > 0) {
                            String key = trimmed.substring(0, eqIndex).trim();
                            String val = trimmed.substring(eqIndex + 1).trim();
                            if ((val.startsWith("\"") && val.endsWith("\"")) ||
                                    (val.startsWith("'") && val.endsWith("'"))) {
                                val = val.substring(1, val.length() - 1);
                            }
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, val);
                            }
                        }
                    }
                    break;
                } catch (Exception ex) {
                    log.warn("Could not read .env file from {}: {}", envFile.getPath(), ex.getMessage());
                }
            }
        }
    }
}

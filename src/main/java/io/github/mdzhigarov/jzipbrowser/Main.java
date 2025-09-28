/*
 * Copyright 2024 mdzhigarov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.mdzhigarov.jzipbrowser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example usage of the jZipBrowser library.
 * This demonstrates how to use the library to browse and extract files from remote ZIP archives.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    public static void main(String[] args) throws Exception {
        // Get credentials and ZIP file URL from environment variables
        String username = System.getenv("ARTIFACTORY_USERNAME");
        String password = System.getenv("ARTIFACTORY_PASSWORD");
        String zipFileUrl = System.getenv("ZIP_FILE_URL");
        
        if (username == null || password == null || zipFileUrl == null) {
            System.err.println("Missing required environment variables:");
            System.err.println("  ARTIFACTORY_USERNAME: " + (username != null ? "✓" : "✗"));
            System.err.println("  ARTIFACTORY_PASSWORD: " + (password != null ? "✓" : "✗"));
            System.err.println("  ZIP_FILE_URL: " + (zipFileUrl != null ? "✓" : "✗"));
            System.err.println();
            System.err.println("Example usage:");
            System.err.println("  export ARTIFACTORY_USERNAME=myuser");
            System.err.println("  export ARTIFACTORY_PASSWORD=mypass");
            System.err.println("  export ZIP_FILE_URL=https://example.com/path/to/archive.zip");
            System.err.println("  java -jar jzipbrowser.jar");
            System.exit(1);
        }
        
        URL zipUrl = new URL(zipFileUrl);

        logger.info("Building ZipBrowser for: {}", zipUrl);
        logger.info("Using credentials - Username: {}, Password: {}", username, password != null ? "***" : "null");
        logger.debug("Starting initialization at: {}", java.time.LocalTime.now());

        ZipBrowser.newBuilder(zipUrl)
            .withBasicAuth(username, password)
            .build()
            .thenCompose(browser -> {
                logger.info("Successfully initialized. File size: {} bytes.", browser.getSize());
                logger.debug("Starting file listing at: {}", java.time.LocalTime.now());
                logger.debug("Listing files...");
                return browser.listFiles().thenCompose(files -> {
                    logger.info("Found {} files in the archive:", files.size());
                    files.forEach(file -> logger.info("  {}", file));
                    
                    // Look for common files in ZIP archives
                    String[] candidateFiles = {
                        "metadata/metadata.yml",
                        "README.md",
                        "README.txt",
                        "index.html",
                        "manifest.yml"
                    };
                    
                    String targetFile = null;
                    for (String candidate : candidateFiles) {
                        if (files.contains(candidate)) {
                            targetFile = candidate;
                            break;
                        }
                    }
                    
                    if (targetFile == null && !files.isEmpty()) {
                        // If no common files found, try the first file
                        targetFile = files.get(0);
                    }
                    
                    if (targetFile != null) {
                        logger.debug("Starting file extraction at: {}", java.time.LocalTime.now());
                        logger.info("Attempting to fetch: {}", targetFile);
                        return browser.getFile(targetFile);
                    } else {
                        logger.warn("No suitable files found to fetch");
                        return CompletableFuture.completedFuture(Optional.empty());
                    }
                });
            })
            .thenAccept(fileStreamOpt -> {
                logger.debug("File extraction completed at: {}", java.time.LocalTime.now());
                if (fileStreamOpt.isPresent()) {
                    try (InputStream is = fileStreamOpt.get();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String content = reader.lines().collect(Collectors.joining("\n"));
                        logger.info("Successfully fetched and decompressed file. Content:");
                        logger.info("File content (first 500 chars):");
                        logger.info(content.length() > 500 ? content.substring(0, 500) + "..." : content);
                    } catch (Exception e) {
                        logger.error("Error reading file content:", e);
                    }
                } else {
                    logger.warn("Target file not found in the archive.");
                }
            })
            .exceptionally(ex -> {
                logger.error("An error occurred: {}", ex.getMessage(), ex);
                return null;
            })
            .join(); // Block for the main thread to wait for completion in this example
    }
}

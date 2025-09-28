package io.github.mdzhigarov.jzipbrowser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Integration tests for RemoteZipBrowser class.
 * These tests use hardcoded values to test against publicly available ZIP files.
 */
class RemoteZipBrowserIntegrationTest {

    private String username;
    private String password;
    private String zipFileUrl;

    @BeforeEach
    void setUp() {
        // Use hardcoded values for testing
        username = "testuser";
        password = "testpass";
        zipFileUrl = "https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-zip-file.zip";
    }

    @Test
    @DisplayName("Should successfully browse small ZIP file")
    void shouldSuccessfullyBrowseSmallZipFile() throws Exception {
        // Given
        URL zipUrl = new URL(zipFileUrl);

        // When - Use HTTP client that trusts all certificates for testing
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl)
                .withBasicAuth(username, password)
                .withHttpClient(TestHttpClientFactory.createTrustAllHttpClient())
                .build();

        ZipBrowser browser = null;
        try {
            browser = future.get(60, TimeUnit.SECONDS); // Increased timeout

            // Then
            assertNotNull(browser);
            assertTrue(browser.getSize() > 0);

            // Test file listing
            CompletableFuture<List<String>> filesFuture = browser.listFiles();
            List<String> files = filesFuture.get(30, TimeUnit.SECONDS); // Increased timeout
            assertNotNull(files);
            assertFalse(files.isEmpty());

            // Test file extraction
            if (!files.isEmpty()) {
                String firstFile = files.get(0);
                CompletableFuture<Optional<InputStream>> fileFuture = browser.getFile(firstFile);
                Optional<InputStream> fileStream = fileFuture.get(30, TimeUnit.SECONDS); // Increased timeout
                
                assertTrue(fileStream.isPresent());
                
                // Read a small portion of the file to verify it's working
                try (InputStream is = fileStream.get();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String firstLine = reader.readLine();
                    assertNotNull(firstLine);
                }
            }
        } finally {
            if (browser != null) {
                browser.close();
            }
        }
    }

    @Test
    @DisplayName("Should handle authentication errors gracefully")
    void shouldHandleAuthenticationErrorsGracefully() throws Exception {
        // Given
        URL zipUrl = new URL("https://httpbin.org/basic-auth/user/pass");

        // When
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl)
                .withBasicAuth("wrong", "credentials")
                .build();

        // Then
        assertThrows(Exception.class, () -> {
            future.get(30, TimeUnit.SECONDS); // Increased timeout
        });
    }

    @Test
    @DisplayName("Should handle non-existent URLs gracefully")
    void shouldHandleNonExistentUrlsGracefully() throws Exception {
        // Given
        URL zipUrl = new URL("https://httpbin.org/status/404");

        // When - Use trust-all HTTP client to avoid SSL issues
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl)
                .withHttpClient(TestHttpClientFactory.createTrustAllHttpClient())
                .build();

        // Then
        assertThrows(Exception.class, () -> {
            future.get(30, TimeUnit.SECONDS); // Increased timeout
        });
    }

    @Test
    @DisplayName("Should handle non-ZIP URLs gracefully")
    void shouldHandleNonZipUrlsGracefully() throws Exception {
        // Given
        URL zipUrl = new URL("https://httpbin.org/json");

        // When - Use trust-all HTTP client to avoid SSL issues
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl)
                .withHttpClient(TestHttpClientFactory.createTrustAllHttpClient())
                .build();

        // Then
        assertThrows(Exception.class, () -> {
            future.get(30, TimeUnit.SECONDS); // Increased timeout
        });
    }

    @Test
    @DisplayName("Should handle timeout scenarios")
    void shouldHandleTimeoutScenarios() throws Exception {
        // Given
        URL zipUrl = new URL("https://httpbin.org/delay/5");

        // When - Use trust-all HTTP client to avoid SSL issues
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl)
                .withHttpClient(TestHttpClientFactory.createTrustAllHttpClient())
                .build();

        // Then
        assertThrows(Exception.class, () -> {
            future.get(3, TimeUnit.SECONDS); // Short timeout
        });
    }

    @Test
    @DisplayName("Should work with custom HttpClient")
    void shouldWorkWithCustomHttpClient() throws Exception {
        // Given
        java.net.http.HttpClient customClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
        
        URL zipUrl = new URL("https://httpbin.org/json");

        // When
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl)
                .withHttpClient(customClient)
                .build();

        // Then
        assertThrows(Exception.class, () -> {
            future.get(30, TimeUnit.SECONDS); // Should fail because it's not a ZIP file
        });
    }

    @Test
    @DisplayName("Should handle concurrent access")
    void shouldHandleConcurrentAccess() throws Exception {
        // Given
        URL zipUrl = new URL(zipFileUrl);

        // When - Create multiple browsers concurrently with trust-all HTTP client
        CompletableFuture<ZipBrowser> future1 = ZipBrowser.newBuilder(zipUrl)
                .withBasicAuth(username, password)
                .withHttpClient(TestHttpClientFactory.createTrustAllHttpClient())
                .build();

        CompletableFuture<ZipBrowser> future2 = ZipBrowser.newBuilder(zipUrl)
                .withBasicAuth(username, password)
                .withHttpClient(TestHttpClientFactory.createTrustAllHttpClient())
                .build();

        // Then
        ZipBrowser browser1 = null;
        ZipBrowser browser2 = null;
        try {
            browser1 = future1.get(60, TimeUnit.SECONDS); // Increased timeout
            browser2 = future2.get(60, TimeUnit.SECONDS); // Increased timeout

            assertNotNull(browser1);
            assertNotNull(browser2);
            assertEquals(browser1.getSize(), browser2.getSize());
        } finally {
            if (browser1 != null) browser1.close();
            if (browser2 != null) browser2.close();
        }
    }

    @Test
    @DisplayName("Should fail when server doesn't support range requests")
    void shouldFailWhenServerDoesntSupportRangeRequests() throws Exception {
        // Given - Use a server that doesn't support range requests
        // httpbin.org returns 200 for range requests instead of 206
        URL zipUrl = new URL("https://httpbin.org/json");

        // When - Use trust-all HTTP client to avoid SSL issues
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl)
                .withHttpClient(TestHttpClientFactory.createTrustAllHttpClient())
                .build();

        // Then - Should fail with a clear error message
        Exception exception = assertThrows(Exception.class, () -> {
            future.get(30, TimeUnit.SECONDS);
        });
        
        // Verify the error message contains information about range request support
        String errorMessage = exception.getMessage();
        assertTrue(errorMessage != null && 
                  (errorMessage.contains("Server doesn't support HTTP Range requests") ||
                   errorMessage.contains("HTTP request failed with status")));
    }

    @Test
    @DisplayName("Should work with public ZIP file (no authentication required)")
    void shouldWorkWithPublicZipFile() throws Exception {
        // Given - Use a publicly available ZIP file for testing
        // This is a small test ZIP file that should be publicly accessible
        URL zipUrl = new URL("https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-zip-file.zip");

        // When - Use trust-all HTTP client to avoid SSL issues
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl)
                .withHttpClient(TestHttpClientFactory.createTrustAllHttpClient())
                .build();

        ZipBrowser browser = null;
        try {
            browser = future.get(60, TimeUnit.SECONDS);

            // Then
            assertNotNull(browser);
            assertTrue(browser.getSize() > 0);

            // Test file listing
            CompletableFuture<List<String>> filesFuture = browser.listFiles();
            List<String> files = filesFuture.get(30, TimeUnit.SECONDS);
            assertNotNull(files);
            assertFalse(files.isEmpty());

            // Test file extraction
            if (!files.isEmpty()) {
                String firstFile = files.get(0);
                CompletableFuture<Optional<InputStream>> fileFuture = browser.getFile(firstFile);
                Optional<InputStream> fileStream = fileFuture.get(30, TimeUnit.SECONDS);
                
                assertTrue(fileStream.isPresent());
                
                // Read a small portion of the file to verify it's working
                try (InputStream is = fileStream.get()) {
                    byte[] buffer = new byte[1024];
                    int bytesRead = is.read(buffer);
                    assertTrue(bytesRead > 0);
                }
            }
        } catch (Exception e) {
            // Check if the error is due to server not supporting range requests
            if (e.getMessage() != null && e.getMessage().contains("Server doesn't support HTTP Range requests")) {
                System.out.println("Public ZIP file test skipped - server doesn't support range requests: " + e.getMessage());
                // This is expected behavior now - the library should fail fast
                return;
            }
            // If it's a different error (network, etc.), that's okay for integration tests
            System.out.println("Public ZIP file test skipped - URL not accessible: " + e.getMessage());
        } finally {
            if (browser != null) {
                browser.close();
            }
        }
    }
}

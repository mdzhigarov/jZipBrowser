package io.github.mdzhigarov.jzipbrowser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.util.concurrent.CompletableFuture;

/**
 * Unit tests for RemoteZipBrowser class.
 */
class RemoteZipBrowserTest {

    private URL testUrl;

    @BeforeEach
    void setUp() throws Exception {
        testUrl = new URL("https://example.com/test.zip");
    }

    @Test
    @DisplayName("Should create builder with valid parameters")
    void shouldCreateBuilderWithValidParameters() {
        // When
        ZipBrowser.Builder builder = ZipBrowser.newBuilder(testUrl)
                .withBasicAuth("user", "pass");

        // Then
        assertNotNull(builder);
    }

    @Test
    @DisplayName("Should return CompletableFuture from build method")
    void shouldReturnCompletableFutureFromBuildMethod() {
        // When
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(testUrl).build();

        // Then
        assertNotNull(future);
        assertFalse(future.isDone());
    }

    @Test
    @DisplayName("Should handle builder chaining")
    void shouldHandleBuilderChaining() {
        // When
        ZipBrowser.Builder builder = ZipBrowser.newBuilder(testUrl)
                .withBasicAuth("user", "pass");

        // Then
        assertNotNull(builder);
        
        // Test that we can call build() (it will fail, but that's expected)
        CompletableFuture<ZipBrowser> future = builder.build();
        assertNotNull(future);
    }
}

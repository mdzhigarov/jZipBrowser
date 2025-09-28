package io.github.mdzhigarov.jzipbrowser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
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
 * Comprehensive integration tests using a local HTTP server with range request support.
 * These tests cover various ZIP file scenarios and edge cases.
 */
class ComprehensiveIntegrationTest {

    private TestHttpServer testServer;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        testServer = new TestHttpServer();
        testServer.start();
        baseUrl = testServer.getBaseUrl();
    }

    @AfterEach
    void tearDown() {
        if (testServer != null) {
            testServer.stop();
        }
    }

    @Test
    @DisplayName("Should handle empty ZIP file")
    void shouldHandleEmptyZipFile() throws Exception {
        // Given
        byte[] emptyZip = ZipFileGenerator.createEmptyZip();
        testServer.addFile("/empty.zip", emptyZip);
        URL zipUrl = new URL(baseUrl + "/empty.zip");

        // When
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl).build();
        ZipBrowser browser = future.get(30, TimeUnit.SECONDS);

        // Then
        assertNotNull(browser);
        assertEquals(emptyZip.length, browser.getSize());

        List<String> files = browser.listFiles().get(10, TimeUnit.SECONDS);
        assertNotNull(files);
        assertTrue(files.isEmpty(), "Empty ZIP should have no files");

        browser.close();
    }

    @Test
    @DisplayName("Should handle single file ZIP (stored, no compression)")
    void shouldHandleSingleFileZip() throws Exception {
        // Given
        byte[] singleFileZip = ZipFileGenerator.createSingleFileZip();
        testServer.addFile("/single.zip", singleFileZip);
        URL zipUrl = new URL(baseUrl + "/single.zip");

        // When
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl).build();
        ZipBrowser browser = future.get(30, TimeUnit.SECONDS);

        // Then
        assertNotNull(browser);
        assertEquals(singleFileZip.length, browser.getSize());

        List<String> files = browser.listFiles().get(10, TimeUnit.SECONDS);
        assertNotNull(files);
        assertEquals(1, files.size());
        assertEquals("hello.txt", files.get(0));

        // Test file extraction
        CompletableFuture<Optional<InputStream>> fileFuture = browser.getFile("hello.txt");
        Optional<InputStream> fileStream = fileFuture.get(10, TimeUnit.SECONDS);
        assertTrue(fileStream.isPresent());

        try (InputStream is = fileStream.get();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String content = reader.readLine();
            assertEquals("Hello, World!", content);
        }

        browser.close();
    }

    @Test
    @DisplayName("Should handle compressed file ZIP")
    void shouldHandleCompressedFileZip() throws Exception {
        // Given
        byte[] compressedZip = ZipFileGenerator.createCompressedFileZip();
        testServer.addFile("/compressed.zip", compressedZip);
        URL zipUrl = new URL(baseUrl + "/compressed.zip");

        // When
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl).build();
        ZipBrowser browser = future.get(30, TimeUnit.SECONDS);

        // Then
        assertNotNull(browser);
        assertEquals(compressedZip.length, browser.getSize());

        List<String> files = browser.listFiles().get(10, TimeUnit.SECONDS);
        assertNotNull(files);
        assertEquals(1, files.size());
        assertEquals("compressed.txt", files.get(0));

        // Test file extraction
        CompletableFuture<Optional<InputStream>> fileFuture = browser.getFile("compressed.txt");
        Optional<InputStream> fileStream = fileFuture.get(10, TimeUnit.SECONDS);
        assertTrue(fileStream.isPresent());

        try (InputStream is = fileStream.get();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String content = reader.readLine();
            assertTrue(content.contains("This is a compressed file"));
            assertTrue(content.contains("repeated content"));
        }

        browser.close();
    }

    @Test
    @DisplayName("Should handle multi-file ZIP")
    void shouldHandleMultiFileZip() throws Exception {
        // Given
        byte[] multiFileZip = ZipFileGenerator.createMultiFileZip();
        testServer.addFile("/multi.zip", multiFileZip);
        URL zipUrl = new URL(baseUrl + "/multi.zip");

        // When
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl).build();
        ZipBrowser browser = future.get(30, TimeUnit.SECONDS);

        // Then
        assertNotNull(browser);
        assertEquals(multiFileZip.length, browser.getSize());

        List<String> files = browser.listFiles().get(10, TimeUnit.SECONDS);
        assertNotNull(files);
        assertEquals(4, files.size());
        assertTrue(files.contains("file1.txt"));
        assertTrue(files.contains("file2.txt"));
        assertTrue(files.contains("subdir/file3.txt"));
        assertTrue(files.contains("subdir/file4.txt"));

        // Test extracting a file from subdirectory
        CompletableFuture<Optional<InputStream>> fileFuture = browser.getFile("subdir/file3.txt");
        Optional<InputStream> fileStream = fileFuture.get(10, TimeUnit.SECONDS);
        assertTrue(fileStream.isPresent());

        try (InputStream is = fileStream.get();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String content = reader.readLine();
            assertEquals("Content of file 3 in subdirectory", content);
        }

        browser.close();
    }

    @Test
    @DisplayName("Should handle ZIP with many files")
    void shouldHandleManyFilesZip() throws Exception {
        // Given
        byte[] manyFilesZip = ZipFileGenerator.createManyFilesZip(100);
        testServer.addFile("/many.zip", manyFilesZip);
        URL zipUrl = new URL(baseUrl + "/many.zip");

        // When
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl).build();
        ZipBrowser browser = future.get(30, TimeUnit.SECONDS);

        // Then
        assertNotNull(browser);
        assertEquals(manyFilesZip.length, browser.getSize());

        List<String> files = browser.listFiles().get(10, TimeUnit.SECONDS);
        assertNotNull(files);
        assertEquals(100, files.size());

        // Test extracting a specific file
        CompletableFuture<Optional<InputStream>> fileFuture = browser.getFile("file0050.txt");
        Optional<InputStream> fileStream = fileFuture.get(10, TimeUnit.SECONDS);
        assertTrue(fileStream.isPresent());

        try (InputStream is = fileStream.get();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String content = reader.readLine();
            assertEquals("Content of file 50", content);
        }

        browser.close();
    }

    @Test
    @DisplayName("Should handle ZIP with directories")
    void shouldHandleDirectoryZip() throws Exception {
        // Given
        byte[] directoryZip = ZipFileGenerator.createDirectoryZip();
        testServer.addFile("/directories.zip", directoryZip);
        URL zipUrl = new URL(baseUrl + "/directories.zip");

        // When
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl).build();
        ZipBrowser browser = future.get(30, TimeUnit.SECONDS);

        // Then
        assertNotNull(browser);
        assertEquals(directoryZip.length, browser.getSize());

        List<String> files = browser.listFiles().get(10, TimeUnit.SECONDS);
        assertNotNull(files);
        assertEquals(3, files.size());
        assertTrue(files.contains("empty_dir/"));
        assertTrue(files.contains("dir_with_files/file.txt"));
        assertTrue(files.contains("level1/level2/level3/nested.txt"));

        // Test extracting a file from nested directory
        CompletableFuture<Optional<InputStream>> fileFuture = browser.getFile("level1/level2/level3/nested.txt");
        Optional<InputStream> fileStream = fileFuture.get(10, TimeUnit.SECONDS);
        assertTrue(fileStream.isPresent());

        try (InputStream is = fileStream.get();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String content = reader.readLine();
            assertEquals("Nested file content", content);
        }

        browser.close();
    }

    @Test
    @DisplayName("Should handle ZIP with special characters in filenames")
    void shouldHandleSpecialCharsZip() throws Exception {
        // Given
        byte[] specialCharsZip = ZipFileGenerator.createSpecialCharsZip();
        testServer.addFile("/special.zip", specialCharsZip);
        URL zipUrl = new URL(baseUrl + "/special.zip");

        // When
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl).build();
        ZipBrowser browser = future.get(30, TimeUnit.SECONDS);

        // Then
        assertNotNull(browser);
        assertEquals(specialCharsZip.length, browser.getSize());

        List<String> files = browser.listFiles().get(10, TimeUnit.SECONDS);
        assertNotNull(files);
        assertEquals(10, files.size());

        // Test extracting a file with special characters
        String specialFileName = "file with spaces.txt";
        assertTrue(files.contains(specialFileName));

        CompletableFuture<Optional<InputStream>> fileFuture = browser.getFile(specialFileName);
        Optional<InputStream> fileStream = fileFuture.get(10, TimeUnit.SECONDS);
        assertTrue(fileStream.isPresent());

        try (InputStream is = fileStream.get();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String content = reader.readLine();
            assertEquals("Content of " + specialFileName, content);
        }

        browser.close();
    }

    @Test
    @DisplayName("Should handle moderate ZIP file")
    void shouldHandleModerateZip() throws Exception {
        // Given
        byte[] moderateZip = ZipFileGenerator.createModerateZip();
        testServer.addFile("/moderate.zip", moderateZip);
        URL zipUrl = new URL(baseUrl + "/moderate.zip");

        // When
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl).build();
        ZipBrowser browser = future.get(30, TimeUnit.SECONDS);

        // Then
        assertNotNull(browser);
        assertEquals(moderateZip.length, browser.getSize());

        List<String> files = browser.listFiles().get(30, TimeUnit.SECONDS);
        assertNotNull(files);
        assertEquals(20, files.size());
        assertTrue(files.contains("moderate_file_00.txt"));
        assertTrue(files.contains("moderate_file_19.txt"));

        // Test extracting a file
        CompletableFuture<Optional<InputStream>> fileFuture = browser.getFile("moderate_file_00.txt");
        Optional<InputStream> fileStream = fileFuture.get(30, TimeUnit.SECONDS);
        assertTrue(fileStream.isPresent());

        try (InputStream is = fileStream.get()) {
            // Read first few bytes to verify content
            byte[] buffer = new byte[1024];
            int bytesRead = is.read(buffer);
            assertTrue(bytesRead > 0);
        }

        browser.close();
    }

    @Test
    @DisplayName("Should handle very large ZIP64 file")
    @Disabled("Disabled by default due to large file size (5GB+) - enable manually for ZIP64 testing")
    void shouldHandleVeryLargeZip64() throws Exception {
        // Given
        byte[] largeZip = ZipFileGenerator.createLargeZip();
        testServer.addFile("/large.zip", largeZip);
        URL zipUrl = new URL(baseUrl + "/large.zip");

        // When
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl).build();
        ZipBrowser browser = future.get(300, TimeUnit.SECONDS); // Very long timeout for 5GB+ file

        // Then
        assertNotNull(browser);
        assertEquals(largeZip.length, browser.getSize());

        List<String> files = browser.listFiles().get(60, TimeUnit.SECONDS);
        assertNotNull(files);
        assertEquals(100, files.size());
        assertTrue(files.contains("large_file_000.txt"));
        assertTrue(files.contains("large_file_099.txt"));

        // Test extracting a file
        CompletableFuture<Optional<InputStream>> fileFuture = browser.getFile("large_file_000.txt");
        Optional<InputStream> fileStream = fileFuture.get(60, TimeUnit.SECONDS);
        assertTrue(fileStream.isPresent());

        try (InputStream is = fileStream.get()) {
            // Read first few bytes to verify content
            byte[] buffer = new byte[1024];
            int bytesRead = is.read(buffer);
            assertTrue(bytesRead > 0);
        }

        browser.close();
    }

    @Test
    @DisplayName("Should handle ZIP with mixed compression methods")
    void shouldHandleMixedCompressionZip() throws Exception {
        // Given
        byte[] mixedZip = ZipFileGenerator.createMixedCompressionZip();
        testServer.addFile("/mixed.zip", mixedZip);
        URL zipUrl = new URL(baseUrl + "/mixed.zip");

        // When
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl).build();
        ZipBrowser browser = future.get(30, TimeUnit.SECONDS);

        // Then
        assertNotNull(browser);
        assertEquals(mixedZip.length, browser.getSize());

        List<String> files = browser.listFiles().get(10, TimeUnit.SECONDS);
        assertNotNull(files);
        assertEquals(2, files.size());
        assertTrue(files.contains("stored.txt"));
        assertTrue(files.contains("compressed.txt"));

        // Test extracting stored file
        CompletableFuture<Optional<InputStream>> storedFuture = browser.getFile("stored.txt");
        Optional<InputStream> storedStream = storedFuture.get(10, TimeUnit.SECONDS);
        assertTrue(storedStream.isPresent());

        try (InputStream is = storedStream.get();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String content = reader.readLine();
            assertEquals("This is stored data", content);
        }

        // Test extracting compressed file
        CompletableFuture<Optional<InputStream>> compressedFuture = browser.getFile("compressed.txt");
        Optional<InputStream> compressedStream = compressedFuture.get(10, TimeUnit.SECONDS);
        assertTrue(compressedStream.isPresent());

        try (InputStream is = compressedStream.get();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String content = reader.readLine();
            assertTrue(content.contains("This is compressed data"));
        }

        browser.close();
    }

    @Test
    @DisplayName("Should handle ZIP with comment")
    void shouldHandleZipWithComment() throws Exception {
        // Given
        byte[] zipWithComment = ZipFileGenerator.createZipWithComment();
        testServer.addFile("/commented.zip", zipWithComment);
        URL zipUrl = new URL(baseUrl + "/commented.zip");

        // When
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl).build();
        ZipBrowser browser = future.get(30, TimeUnit.SECONDS);

        // Then
        assertNotNull(browser);
        assertEquals(zipWithComment.length, browser.getSize());

        List<String> files = browser.listFiles().get(10, TimeUnit.SECONDS);
        assertNotNull(files);
        assertEquals(1, files.size());
        assertEquals("test.txt", files.get(0));

        // Test file extraction
        CompletableFuture<Optional<InputStream>> fileFuture = browser.getFile("test.txt");
        Optional<InputStream> fileStream = fileFuture.get(10, TimeUnit.SECONDS);
        assertTrue(fileStream.isPresent());

        try (InputStream is = fileStream.get();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String content = reader.readLine();
            assertEquals("Test file content", content);
        }

        browser.close();
    }

    @Test
    @DisplayName("Should handle non-existent file gracefully")
    void shouldHandleNonExistentFile() throws Exception {
        // Given
        byte[] singleFileZip = ZipFileGenerator.createSingleFileZip();
        testServer.addFile("/single.zip", singleFileZip);
        URL zipUrl = new URL(baseUrl + "/single.zip");

        // When
        CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl).build();
        ZipBrowser browser = future.get(30, TimeUnit.SECONDS);

        // Then
        assertNotNull(browser);

        // Try to get a non-existent file
        CompletableFuture<Optional<InputStream>> fileFuture = browser.getFile("nonexistent.txt");
        Optional<InputStream> fileStream = fileFuture.get(10, TimeUnit.SECONDS);
        assertFalse(fileStream.isPresent());

        browser.close();
    }

    @Test
    @DisplayName("Should handle concurrent access to same ZIP file")
    void shouldHandleConcurrentAccess() throws Exception {
        // Given
        byte[] multiFileZip = ZipFileGenerator.createMultiFileZip();
        testServer.addFile("/multi.zip", multiFileZip);
        URL zipUrl = new URL(baseUrl + "/multi.zip");

        // When - Create multiple browsers concurrently
        CompletableFuture<ZipBrowser> future1 = ZipBrowser.newBuilder(zipUrl).build();
        CompletableFuture<ZipBrowser> future2 = ZipBrowser.newBuilder(zipUrl).build();

        // Then
        ZipBrowser browser1 = future1.get(30, TimeUnit.SECONDS);
        ZipBrowser browser2 = future2.get(30, TimeUnit.SECONDS);

        assertNotNull(browser1);
        assertNotNull(browser2);
        assertEquals(browser1.getSize(), browser2.getSize());

        // Test concurrent file access
        CompletableFuture<List<String>> files1 = browser1.listFiles();
        CompletableFuture<List<String>> files2 = browser2.listFiles();

        List<String> filesList1 = files1.get(10, TimeUnit.SECONDS);
        List<String> filesList2 = files2.get(10, TimeUnit.SECONDS);

        assertEquals(filesList1.size(), filesList2.size());
        assertEquals(filesList1, filesList2);

        browser1.close();
        browser2.close();
    }
}

package io.github.mdzhigarov.jzipbrowser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ZipEntry class.
 */
class ZipEntryTest {

    @Test
    @DisplayName("Should create ZipEntry with all properties")
    void shouldCreateZipEntryWithAllProperties() {
        // Given
        String name = "test/file.txt";
        long localHeaderOffset = 100L;
        long compressedSize = 1024L;
        long uncompressedSize = 2048L;
        int compressionMethod = 8;
        long crc32 = 0x12345678L;
        boolean isDirectory = false;
        int fileNameLength = 12;
        int extraFieldLength = 24;

        // When
        ZipEntry entry = new ZipEntry(name, localHeaderOffset, compressedSize, uncompressedSize,
                compressionMethod, crc32, isDirectory, fileNameLength, extraFieldLength);

        // Then
        assertEquals(name, entry.getName());
        assertEquals(localHeaderOffset, entry.getLocalHeaderOffset());
        assertEquals(compressedSize, entry.getCompressedSize());
        assertEquals(uncompressedSize, entry.getUncompressedSize());
        assertEquals(compressionMethod, entry.getCompressionMethod());
        assertEquals(crc32, entry.getCrc32());
        assertEquals(isDirectory, entry.isDirectory());
        assertEquals(fileNameLength, entry.getFileNameLength());
        assertEquals(extraFieldLength, entry.getExtraFieldLength());
    }

    @Test
    @DisplayName("Should identify compressed files correctly")
    void shouldIdentifyCompressedFilesCorrectly() {
        // Given - deflated file (compression method 8)
        ZipEntry compressedEntry = new ZipEntry("compressed.txt", 0, 100, 200, 8, 0, false, 12, 0);
        
        // Given - stored file (compression method 0)
        ZipEntry storedEntry = new ZipEntry("stored.txt", 0, 200, 200, 0, 0, false, 10, 0);

        // Then
        assertTrue(compressedEntry.isCompressed());
        assertFalse(storedEntry.isCompressed());
    }

    @Test
    @DisplayName("Should identify directories correctly")
    void shouldIdentifyDirectoriesCorrectly() {
        // Given - directory with trailing slash
        ZipEntry directoryWithSlash = new ZipEntry("folder/", 0, 0, 0, 0, 0, true, 7, 0);
        
        // Given - regular file
        ZipEntry regularFile = new ZipEntry("file.txt", 0, 100, 100, 0, 0, false, 8, 0);

        // Then
        assertTrue(directoryWithSlash.isDirectory());
        assertFalse(regularFile.isDirectory());
    }

    @Test
    @DisplayName("Should handle large file sizes")
    void shouldHandleLargeFileSizes() {
        // Given - large file sizes (ZIP64 range)
        long largeSize = 5_000_000_000L; // 5GB
        
        // When
        ZipEntry largeEntry = new ZipEntry("large.bin", 0, largeSize, largeSize, 0, 0, false, 8, 0);

        // Then
        assertEquals(largeSize, largeEntry.getCompressedSize());
        assertEquals(largeSize, largeEntry.getUncompressedSize());
    }

    @Test
    @DisplayName("Should handle special characters in file names")
    void shouldHandleSpecialCharactersInFileNames() {
        // Given - file name with special characters
        String specialName = "file with spaces & symbols!.txt";
        
        // When
        ZipEntry entry = new ZipEntry(specialName, 0, 100, 100, 0, 0, false, specialName.length(), 0);

        // Then
        assertEquals(specialName, entry.getName());
        assertEquals(specialName.length(), entry.getFileNameLength());
    }

    @Test
    @DisplayName("Should handle empty file names")
    void shouldHandleEmptyFileNames() {
        // Given - empty file name
        String emptyName = "";
        
        // When
        ZipEntry entry = new ZipEntry(emptyName, 0, 0, 0, 0, 0, false, 0, 0);

        // Then
        assertEquals(emptyName, entry.getName());
        assertEquals(0, entry.getFileNameLength());
    }
}

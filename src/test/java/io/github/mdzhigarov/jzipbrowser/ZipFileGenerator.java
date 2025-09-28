package io.github.mdzhigarov.jzipbrowser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Utility class for generating various types of ZIP files for testing.
 */
public class ZipFileGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ZipFileGenerator.class);
    
    /**
     * Creates an empty ZIP file.
     */
    public static byte[] createEmptyZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Empty ZIP - no entries
        }
        return baos.toByteArray();
    }
    
    /**
     * Creates a ZIP file with a single small text file (stored, no compression).
     */
    public static byte[] createSingleFileZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            byte[] content = "Hello, World!".getBytes();
            
            ZipEntry entry = new ZipEntry("hello.txt");
            entry.setMethod(ZipEntry.STORED); // No compression
            entry.setSize(content.length);
            
            // Calculate CRC32
            java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
            crc32.update(content);
            entry.setCrc(crc32.getValue());
            
            zos.putNextEntry(entry);
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Creates a ZIP file with a single compressed file.
     */
    public static byte[] createCompressedFileZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("compressed.txt");
            entry.setMethod(ZipEntry.DEFLATED); // Compressed
            zos.putNextEntry(entry);
            zos.write("This is a compressed file with some repeated content. ".repeat(10).getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Creates a ZIP file with multiple files.
     */
    public static byte[] createMultiFileZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add several files
            String[] files = {
                "file1.txt", "file2.txt", "subdir/file3.txt", "subdir/file4.txt"
            };
            String[] contents = {
                "Content of file 1",
                "Content of file 2 with more text",
                "Content of file 3 in subdirectory",
                "Content of file 4 in subdirectory with even more text"
            };
            
            for (int i = 0; i < files.length; i++) {
                ZipEntry entry = new ZipEntry(files[i]);
                zos.putNextEntry(entry);
                zos.write(contents[i].getBytes());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
    
    /**
     * Creates a ZIP file with many small files (tests central directory performance).
     */
    public static byte[] createManyFilesZip(int fileCount) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < fileCount; i++) {
                ZipEntry entry = new ZipEntry(String.format("file%04d.txt", i));
                zos.putNextEntry(entry);
                zos.write(String.format("Content of file %d", i).getBytes());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
    
    /**
     * Creates a ZIP file with directories (empty and with files).
     */
    public static byte[] createDirectoryZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Empty directory
            ZipEntry emptyDir = new ZipEntry("empty_dir/");
            zos.putNextEntry(emptyDir);
            zos.closeEntry();
            
            // Directory with files
            ZipEntry fileInDir = new ZipEntry("dir_with_files/file.txt");
            zos.putNextEntry(fileInDir);
            zos.write("File in directory".getBytes());
            zos.closeEntry();
            
            // Nested directories
            ZipEntry nestedFile = new ZipEntry("level1/level2/level3/nested.txt");
            zos.putNextEntry(nestedFile);
            zos.write("Nested file content".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Creates a ZIP file with special characters in filenames.
     */
    public static byte[] createSpecialCharsZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            String[] specialNames = {
                "file with spaces.txt",
                "file-with-dashes.txt",
                "file_with_underscores.txt",
                "file.with.dots.txt",
                "file(with)parentheses.txt",
                "file[with]brackets.txt",
                "file{with}braces.txt",
                "file@with#symbols$.txt",
                "файл-с-кириллицей.txt", // Cyrillic
                "文件-中文.txt" // Chinese
            };
            
            for (String name : specialNames) {
                ZipEntry entry = new ZipEntry(name);
                zos.putNextEntry(entry);
                zos.write(("Content of " + name).getBytes());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
    
    /**
     * Creates a moderately large ZIP file for testing (not ZIP64).
     * This creates a file that's large enough to test performance but not trigger ZIP64.
     */
    public static byte[] createModerateZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Create 20 files of 10MB each = 200MB total uncompressed
            for (int i = 0; i < 20; i++) {
                ZipEntry entry = new ZipEntry(String.format("moderate_file_%02d.txt", i));
                zos.putNextEntry(entry);
                
                // Write 10MB per file
                long fileSize = 10L * 1024 * 1024; // 10MB
                long written = 0;
                
                // Write in chunks to avoid memory issues
                byte[] chunk = new byte[1024 * 1024]; // 1MB chunks
                while (written < fileSize) {
                    int chunkSize = (int) Math.min(chunk.length, fileSize - written);
                    
                    // Fill chunk with pattern data
                    for (int j = 0; j < chunkSize; j++) {
                        chunk[j] = (byte) ((written + j) % 256);
                    }
                    
                    zos.write(chunk, 0, chunkSize);
                    written += chunkSize;
                }
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    /**
     * Creates a large ZIP file that definitely triggers ZIP64 format.
     * This creates a file that's large enough to require ZIP64 (>4GB).
     * Note: This method creates a very large file and may take significant time and memory.
     */
    public static byte[] createLargeZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Create a file that's large enough to definitely trigger ZIP64
            // We'll create 100 files of 50MB each = 5GB total uncompressed
            for (int i = 0; i < 100; i++) {
                ZipEntry entry = new ZipEntry(String.format("large_file_%03d.txt", i));
                zos.putNextEntry(entry);
                
                // Write 50MB per file (50 * 1024 * 1024 bytes)
                long fileSize = 50L * 1024 * 1024; // 50MB
                long written = 0;
                
                // Write in chunks to avoid memory issues
                byte[] chunk = new byte[1024 * 1024]; // 1MB chunks
                while (written < fileSize) {
                    int chunkSize = (int) Math.min(chunk.length, fileSize - written);
                    
                    // Fill chunk with pattern data
                    for (int j = 0; j < chunkSize; j++) {
                        chunk[j] = (byte) ((written + j) % 256);
                    }
                    
                    zos.write(chunk, 0, chunkSize);
                    written += chunkSize;
                }
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
    
    /**
     * Creates a ZIP file with mixed compression methods.
     */
    public static byte[] createMixedCompressionZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Stored (no compression) file
            byte[] storedContent = "This is stored data".getBytes();
            ZipEntry storedEntry = new ZipEntry("stored.txt");
            storedEntry.setMethod(ZipEntry.STORED);
            storedEntry.setSize(storedContent.length);
            
            // Calculate CRC32 for stored content
            java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
            crc32.update(storedContent);
            storedEntry.setCrc(crc32.getValue());
            
            zos.putNextEntry(storedEntry);
            zos.write(storedContent);
            zos.closeEntry();
            
            // Compressed file
            ZipEntry compressedEntry = new ZipEntry("compressed.txt");
            compressedEntry.setMethod(ZipEntry.DEFLATED);
            zos.putNextEntry(compressedEntry);
            zos.write("This is compressed data with repeated content. ".repeat(50).getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Creates a ZIP file with a comment.
     */
    public static byte[] createZipWithComment() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("test.txt");
            zos.putNextEntry(entry);
            zos.write("Test file content".getBytes());
            zos.closeEntry();
            
            // Set comment
            zos.setComment("This is a test ZIP file with a comment");
        }
        return baos.toByteArray();
    }
    
    /**
     * Saves a ZIP file to disk for inspection.
     */
    public static Path saveZipToFile(byte[] zipData, String filename) throws IOException {
        Path tempDir = Files.createTempDirectory("jzipbrowser-test");
        Path zipFile = tempDir.resolve(filename);
        Files.write(zipFile, zipData);
        logger.info("Saved ZIP file to: {}", zipFile);
        return zipFile;
    }
    
    /**
     * Gets the size of a ZIP file in a human-readable format.
     */
    public static String getSizeString(byte[] zipData) {
        long size = zipData.length;
        if (size < 1024) {
            return size + " bytes";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }
}

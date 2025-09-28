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

package com.jzipbrowser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.InflaterInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implementation of ZipBrowser that reads from remote ZIP files using HTTP Range requests.
 * This class efficiently extracts individual files from large ZIP archives without downloading
 * the entire file by parsing the central directory and using HTTP Range requests.
 */
public class RemoteZipBrowser implements ZipBrowser {
    
    private static final Logger logger = LoggerFactory.getLogger(RemoteZipBrowser.class);
    
    private static final int EOCD_SIGNATURE = 0x06054b50; // "PK\005\006"
    private static final int CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50; // "PK\001\002"
    private static final int ZIP64_EOCD_SIGNATURE = 0x06064b50; // "PK\006\006"
    private static final int ZIP64_EOCD_LOCATOR_SIGNATURE = 0x07064b50; // "PK\006\007"
    private static final int MAX_EOCD_SEARCH_SIZE = 65536; // 64KB max comment size
    private static final int INITIAL_EOCD_SEARCH_SIZE = 1024; // 1KB initial search
    private static final long ZIP64_MARKER = 0xFFFFFFFFL; // Indicates ZIP64 values
    
    private final URL zipUrl;
    private final HttpClient httpClient;
    private final String basicAuth;
    private final long fileSize;
    private final Map<String, ZipEntry> entries;
    private volatile boolean closed = false;

    private RemoteZipBrowser(URL zipUrl, HttpClient httpClient, String basicAuth, 
                           long fileSize, Map<String, ZipEntry> entries) {
        this.zipUrl = zipUrl;
        this.httpClient = httpClient;
        this.basicAuth = basicAuth;
        this.fileSize = fileSize;
        this.entries = new ConcurrentHashMap<>(entries);
    }

    @Override
    public CompletableFuture<List<String>> listFiles() {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("ZipBrowser is closed"));
        }
        
        return CompletableFuture.completedFuture(new ArrayList<>(entries.keySet()));
    }

    @Override
    public CompletableFuture<Optional<InputStream>> getFile(String fileName) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("ZipBrowser is closed"));
        }
        
        ZipEntry entry = entries.get(fileName);
        if (entry == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        
        if (entry.isDirectory()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        
        return fetchFileData(entry)
            .thenApply(data -> Optional.of(createInputStream(data, entry)));
    }

    @Override
    public long getSize() {
        return fileSize;
    }

    @Override
    public void close() {
        closed = true;
    }

    /**
     * Fetches the compressed data for a specific file entry using HTTP Range request.
     * This method first reads the local file header to get accurate size information,
     * then fetches the actual compressed data in a single HTTP request.
     */
    private CompletableFuture<byte[]> fetchFileData(ZipEntry entry) {
        long headerStart = entry.getLocalHeaderOffset();
        long headerEnd = headerStart + 30; // Local file header is 30 bytes minimum
        
        logger.debug("File entry details for {}: local header offset={}, file name length={}, extra field length={}, compressed size={}, uncompressed size={}", 
                    entry.getName(), entry.getLocalHeaderOffset(), entry.getFileNameLength(), 
                    entry.getExtraFieldLength(), entry.getCompressedSize(), entry.getUncompressedSize());
        
        return fetchRange(headerStart, headerEnd)
            .thenCompose(headerData -> {
                try {
                    // Parse local file header structure
                    ByteBuffer buffer = ByteBuffer.wrap(headerData);
                    buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    
                    // Validate local file header signature
                    int signature = buffer.getInt();
                    if (signature != 0x04034b50) { // "PK\003\004"
                        throw new IOException("Invalid local file header signature");
                    }
                    
                    // Parse local file header fields
                    buffer.getShort(); // Skip version needed to extract
                    buffer.getShort(); // Skip general purpose bit flag
                    int localCompressionMethod = buffer.getShort() & 0xFFFF;
                    buffer.getShort(); // Skip last mod file time
                    buffer.getShort(); // Skip last mod file date
                    buffer.getInt(); // Skip CRC-32
                    long localCompressedSize = buffer.getInt() & 0xFFFFFFFFL;
                    long localUncompressedSize = buffer.getInt() & 0xFFFFFFFFL;
                    int localFileNameLength = buffer.getShort() & 0xFFFF;
                    int localExtraFieldLength = buffer.getShort() & 0xFFFF;
                    
                    logger.debug("Local header: compression method={}, compressed size={}, uncompressed size={}, file name length={}, extra field length={}", 
                                localCompressionMethod, localCompressedSize, localUncompressedSize, localFileNameLength, localExtraFieldLength);
                    
                    // Determine actual file sizes - handle ZIP64 and streaming formats
                    long actualCompressedSize = localCompressedSize;
                    long actualUncompressedSize = localUncompressedSize;
                    
                    // Check for ZIP64 markers or streaming format (zero sizes in local header)
                    if (isZip64OrStreamingFormat(localCompressedSize, localUncompressedSize)) {
                        logger.debug("Local header has ZIP64 markers or zero sizes, using central directory values");
                        actualCompressedSize = entry.getCompressedSize();
                        actualUncompressedSize = entry.getUncompressedSize();
                        logger.debug("Using central dir compressed size: {}, uncompressed size: {}", actualCompressedSize, actualUncompressedSize);
                        
                        // Calculate actual data offset using local header values
                        long dataOffset = headerStart + 30 + localFileNameLength + localExtraFieldLength;
                        long dataEnd = dataOffset + actualCompressedSize - 1;
                        
                        logger.debug("Calculated data offset: {}, data range: {}-{}", dataOffset, dataOffset, dataEnd);
                        
                        // Fetch the actual file data
                        return fetchRange(dataOffset, dataEnd);
                    } else {
                        // Calculate actual data offset using local header values
                        long dataOffset = headerStart + 30 + localFileNameLength + localExtraFieldLength;
                        long dataEnd = dataOffset + localCompressedSize - 1;
                        
                        logger.debug("Calculated data offset: {}, data range: {}-{}", dataOffset, dataOffset, dataEnd);
                        
                        // Fetch the actual file data
                        return fetchRange(dataOffset, dataEnd);
                    }
                } catch (Exception e) {
                    return CompletableFuture.failedFuture(e);
                }
            });
    }

    /**
     * Checks if the local header indicates ZIP64 format or streaming format.
     * ZIP64 uses 0xFFFFFFFF markers, streaming format uses zero sizes.
     */
    private static boolean isZip64OrStreamingFormat(long compressedSize, long uncompressedSize) {
        return compressedSize == ZIP64_MARKER || uncompressedSize == ZIP64_MARKER || 
               compressedSize == 0 || uncompressedSize == 0;
    }

    /**
     * Checks if the EOCD indicates a ZIP64 format based on marker values.
     */
    private static boolean isZip64Format(long centralDirSize, long centralDirOffset, int totalEntries) {
        return centralDirSize == ZIP64_MARKER || centralDirOffset == ZIP64_MARKER || totalEntries == 0xFFFF;
    }

    /**
     * Checks if central directory entry has ZIP64 markers indicating 64-bit values.
     */
    private static boolean hasZip64Markers(long compressedSize, long uncompressedSize, long localHeaderOffset) {
        return compressedSize == ZIP64_MARKER || uncompressedSize == ZIP64_MARKER || localHeaderOffset == ZIP64_MARKER;
    }

    /**
     * Creates an InputStream that decompresses the data on-the-fly.
     */
    private InputStream createInputStream(byte[] compressedData, ZipEntry entry) {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
        
        logger.debug("Decompression info - Compression method: {}, compressed size: {}, uncompressed size: {}", 
                    entry.getCompressionMethod(), compressedData.length, entry.getUncompressedSize());
        
        // Check if this is actually a stored (uncompressed) file
        if (entry.getCompressionMethod() == 0) {
            logger.debug("Using raw data for stored (uncompressed) file");
            return bais;
        } else if (entry.getCompressionMethod() == 8) {
            // Use InflaterInputStream for deflated data
            logger.debug("Using InflaterInputStream for deflated data");
            return new InflaterInputStream(bais, new java.util.zip.Inflater(true));
        } else {
            logger.warn("Unknown compression method: {}, trying as raw data", entry.getCompressionMethod());
            return bais;
        }
    }

    /**
     * Fetches a range of bytes from the remote file using HTTP Range request.
     */
    private CompletableFuture<byte[]> fetchRange(long start, long end) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(zipUrl.toString()))
            .header("Range", "bytes=" + start + "-" + end);
        
        if (basicAuth != null) {
            requestBuilder.header("Authorization", "Basic " + basicAuth);
        }
        
        HttpRequest request = requestBuilder.build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .thenApply(response -> {
                if (response.statusCode() == 206) { // Partial Content
                    return response.body();
                } else if (response.statusCode() == 200) {
                    // Server doesn't support range requests - fail fast
                    throw new RuntimeException("Server doesn't support HTTP Range requests (returned 200 instead of 206). " +
                            "This library requires servers that support HTTP Range requests for efficient ZIP file browsing.");
                } else {
                    throw new RuntimeException("HTTP request failed with status: " + response.statusCode());
                }
            });
    }

    /**
     * Builder class for creating RemoteZipBrowser instances.
     */
    public static class Builder implements ZipBrowser.Builder {
        private final URL url;
        private HttpClient httpClient;
        private String username;
        private String password;

        public Builder(URL url) {
            this.url = url;
        }

        @Override
        public Builder withBasicAuth(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        @Override
        public Builder withHttpClient(HttpClient client) {
            this.httpClient = client;
            return this;
        }

        @Override
        public CompletableFuture<ZipBrowser> build() {
            HttpClient client = this.httpClient != null ? this.httpClient : 
                HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)  // Explicitly use HTTP/1.1 for Range request support
                    .connectTimeout(java.time.Duration.ofSeconds(30))  // Connection timeout
                    .build();
            String basicAuth = (username != null && password != null) ? 
                Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8)) : null;
            
            return getFileSize(client, basicAuth)
                .thenCompose(size -> parseCentralDirectory(client, basicAuth, size))
                .thenApply(entries -> new RemoteZipBrowser(url, client, basicAuth, 
                    getFileSize(client, basicAuth).join(), entries));
        }

        /**
         * Gets the file size using HTTP HEAD request.
         */
        private CompletableFuture<Long> getFileSize(HttpClient client, String basicAuth) {
            logger.debug("Getting file size via HTTP HEAD request using HTTP version: {}", client.version());
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .header("User-Agent", "jZipBrowser/1.0.0")
                .header("Accept", "*/*");
            
            if (basicAuth != null) {
                requestBuilder.header("Authorization", "Basic " + basicAuth);
            }
            
            HttpRequest request = requestBuilder.build();
            
            return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> {
                    logger.debug("HTTP HEAD response status: {} (Server HTTP version: {})", response.statusCode(), response.version());
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Failed to get file size. HTTP status: " + response.statusCode());
                    }
                    
                    String contentLength = response.headers().firstValue("Content-Length").orElse(null);
                    if (contentLength == null) {
                        throw new RuntimeException("Server did not provide Content-Length header");
                    }
                    
                    try {
                        long size = Long.parseLong(contentLength);
                        logger.debug("File size: {} bytes ({} MB)", size, size / 1024 / 1024);
                        return size;
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("Invalid Content-Length header: " + contentLength);
                    }
                });
        }

        /**
         * Parses the central directory of the ZIP file.
         */
        private CompletableFuture<Map<String, ZipEntry>> parseCentralDirectory(HttpClient client, String basicAuth, long fileSize) {
            logger.debug("Finding End of Central Directory record...");
            return findEndOfCentralDirectory(client, basicAuth, fileSize)
                .thenCompose(eocd -> {
                    long centralDirOffset = eocd.getCentralDirectoryOffset();
                    long centralDirSize = eocd.getCentralDirectorySize();
                    
                    logger.debug("Found EOCD - Central Directory at offset: {}, size: {} bytes", centralDirOffset, centralDirSize);
                    logger.debug("Downloading central directory...");
                    
                    return fetchRange(client, basicAuth, centralDirOffset, centralDirOffset + centralDirSize - 1)
                        .thenApply(data -> {
                            logger.debug("Downloaded central directory: {} bytes", data.length);
                            logger.debug("Parsing central directory entries...");
                            Map<String, ZipEntry> entries = parseCentralDirectoryEntries(data, centralDirOffset);
                            logger.debug("Parsed {} file entries", entries.size());
                            return entries;
                        });
                });
        }

        /**
         * Finds the End of Central Directory (EOCD) record by searching backwards from the end of the file.
         * This method handles both standard ZIP and ZIP64 formats.
         */
        private CompletableFuture<EndOfCentralDirectory> findEndOfCentralDirectory(HttpClient client, String basicAuth, long fileSize) {
            return findEndOfCentralDirectoryRecursive(client, basicAuth, fileSize, INITIAL_EOCD_SEARCH_SIZE);
        }

        /**
         * Recursively searches for the EOCD record, expanding the search size if not found.
         * This handles cases where the ZIP comment is larger than expected.
         */
        private CompletableFuture<EndOfCentralDirectory> findEndOfCentralDirectoryRecursive(
                HttpClient client, String basicAuth, long fileSize, int searchSize) {
            
            if (searchSize > MAX_EOCD_SEARCH_SIZE) {
                return CompletableFuture.failedFuture(new RuntimeException("Could not find End of Central Directory record"));
            }
            
            long start = Math.max(0, fileSize - searchSize);
            long end = fileSize - 1;
            
            logger.debug("Searching for EOCD in last {} bytes (range: {}-{})", searchSize, start, end);
            logger.debug("Looking for EOCD signature: 0x{}", Integer.toHexString(EOCD_SIGNATURE).toUpperCase());
            
            return fetchRange(client, basicAuth, start, end)
                .thenCompose(data -> {
                    logger.debug("Downloaded {} bytes for EOCD search", data.length);
                    
                    // Search backwards for EOCD signature (Little-Endian byte order)
                    for (int i = data.length - 4; i >= 0; i--) {
                        int signature = ((data[i] & 0xFF) | 
                                       ((data[i + 1] & 0xFF) << 8) | 
                                       ((data[i + 2] & 0xFF) << 16) | 
                                       ((data[i + 3] & 0xFF) << 24));
                        
                        if (signature == EOCD_SIGNATURE) {
                            logger.debug("Found EOCD signature at position {} in search chunk", i);
                            logger.debug("Signature value: 0x{}", Integer.toHexString(signature).toUpperCase());
                            
                            // Found EOCD signature, parse the record
                            if (i + 22 > data.length) {
                                logger.warn("EOCD record incomplete, expanding search to {} bytes", searchSize * 2);
                                // EOCD record is incomplete, need to fetch more data
                                return findEndOfCentralDirectoryRecursive(client, basicAuth, fileSize, searchSize * 2);
                            }
                            
                            ByteBuffer buffer = ByteBuffer.wrap(data, i, 22);
                            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN); // ZIP files use little-endian byte order
                            buffer.getInt(); // Skip signature
                            
                            buffer.getShort(); // Skip diskNumber
                            buffer.getShort(); // Skip centralDirDisk
                            buffer.getShort(); // Skip entriesOnDisk
                            int totalEntries = buffer.getShort() & 0xFFFF;
                            long centralDirSize = buffer.getInt() & 0xFFFFFFFFL;
                            long centralDirOffset = buffer.getInt() & 0xFFFFFFFFL;
                            buffer.getShort(); // Skip commentLength
                            
                            logger.debug("EOCD parsed - Total entries: {}, Central dir size: {}, Central dir offset: {}", 
                                        totalEntries, centralDirSize, centralDirOffset);
                            
                            // Check if this is a ZIP64 file (indicated by 0xFFFFFFFF markers)
                            if (isZip64Format(centralDirSize, centralDirOffset, totalEntries)) {
                                logger.debug("ZIP64 detected - searching for ZIP64 EOCD locator...");
                                return findZIP64EndOfCentralDirectory(client, basicAuth, fileSize, i + start);
                            }
                            
                            // Validate EOCD values
                            if (centralDirOffset < 0 || centralDirOffset >= fileSize) {
                                throw new RuntimeException("Invalid central directory offset: " + centralDirOffset);
                            }
                            if (centralDirSize < 0 || centralDirSize > fileSize) {
                                throw new RuntimeException("Invalid central directory size: " + centralDirSize);
                            }
                            if (centralDirOffset + centralDirSize > fileSize) {
                                throw new RuntimeException("Central directory extends beyond file size");
                            }
                            
                            return CompletableFuture.completedFuture(
                                new EndOfCentralDirectory(centralDirOffset, centralDirSize, totalEntries));
                        }
                    }
                    
                    logger.warn("EOCD not found in {} bytes, expanding search to {} bytes", searchSize, searchSize * 2);
                    // EOCD not found in this chunk, try with a larger chunk
                    return findEndOfCentralDirectoryRecursive(client, basicAuth, fileSize, searchSize * 2);
                });
        }

        /**
         * Finds and parses the ZIP64 End of Central Directory record.
         * ZIP64 format uses a locator record that points to the actual ZIP64 EOCD.
         */
        private CompletableFuture<EndOfCentralDirectory> findZIP64EndOfCentralDirectory(
                HttpClient client, String basicAuth, long fileSize, long eocdOffset) {
            
            // ZIP64 EOCD locator is located immediately before the standard EOCD
            long locatorStart = eocdOffset - 20; // ZIP64 EOCD locator is exactly 20 bytes
            long locatorEnd = eocdOffset - 1;
            
            logger.debug("Searching for ZIP64 EOCD locator at offset: {}", locatorStart);
            
            return fetchRange(client, basicAuth, locatorStart, locatorEnd)
                .thenCompose(locatorData -> {
                    if (locatorData.length < 20) {
                        throw new RuntimeException("ZIP64 EOCD locator too small");
                    }
                    
                    ByteBuffer buffer = ByteBuffer.wrap(locatorData);
                    buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    
                    int signature = buffer.getInt();
                    if (signature != ZIP64_EOCD_LOCATOR_SIGNATURE) {
                        throw new RuntimeException("Invalid ZIP64 EOCD locator signature");
                    }
                    
                    buffer.getInt(); // Skip disk number
                    long zip64EOCDOffset = buffer.getLong(); // ZIP64 EOCD offset (8 bytes)
                    buffer.getInt(); // Skip total disks
                    
                    logger.debug("Found ZIP64 EOCD locator - ZIP64 EOCD at offset: {}", zip64EOCDOffset);
                    
                    // Fetch the ZIP64 EOCD record
                    return fetchRange(client, basicAuth, zip64EOCDOffset, zip64EOCDOffset + 56 - 1) // ZIP64 EOCD is 56 bytes
                        .thenApply(zip64Data -> {
                            if (zip64Data.length < 56) {
                                throw new RuntimeException("ZIP64 EOCD record too small");
                            }
                            
                            ByteBuffer zip64Buffer = ByteBuffer.wrap(zip64Data);
                            zip64Buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
                            
                            int zip64Signature = zip64Buffer.getInt();
                            if (zip64Signature != ZIP64_EOCD_SIGNATURE) {
                                throw new RuntimeException("Invalid ZIP64 EOCD signature");
                            }
                            
                            zip64Buffer.getLong(); // Skip size of ZIP64 EOCD record
                            zip64Buffer.getShort(); // Skip version made by
                            zip64Buffer.getShort(); // Skip version needed
                            zip64Buffer.getInt(); // Skip disk number
                            zip64Buffer.getInt(); // Skip central directory disk
                            long totalEntries = zip64Buffer.getLong(); // Total entries (8 bytes)
                            zip64Buffer.getLong(); // Skip entries on disk
                            long centralDirSize = zip64Buffer.getLong(); // Central directory size (8 bytes)
                            long centralDirOffset = zip64Buffer.getLong(); // Central directory offset (8 bytes)
                            
                            logger.debug("ZIP64 EOCD parsed - Total entries: {}, Central dir size: {}, Central dir offset: {}", 
                                        totalEntries, centralDirSize, centralDirOffset);
                            
                            // Validate ZIP64 EOCD values
                            if (centralDirOffset < 0 || centralDirOffset >= fileSize) {
                                throw new RuntimeException("Invalid ZIP64 central directory offset: " + centralDirOffset);
                            }
                            if (centralDirSize < 0 || centralDirSize > fileSize) {
                                throw new RuntimeException("Invalid ZIP64 central directory size: " + centralDirSize);
                            }
                            if (centralDirOffset + centralDirSize > fileSize) {
                                throw new RuntimeException("ZIP64 central directory extends beyond file size");
                            }
                            
                            return new EndOfCentralDirectory(centralDirOffset, centralDirSize, (int) totalEntries);
                        });
                });
        }

        /**
         * Parses the central directory entries from the downloaded data.
         * Each entry contains metadata about a file in the ZIP archive.
         */
        private Map<String, ZipEntry> parseCentralDirectoryEntries(byte[] data, long centralDirOffset) {
            Map<String, ZipEntry> entries = new HashMap<>();
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN); // ZIP format uses little-endian byte order
            
            while (buffer.hasRemaining()) {
                if (buffer.remaining() < 46) break; // Minimum central directory entry size
                
                int signature = buffer.getInt();
                if (signature != CENTRAL_DIRECTORY_SIGNATURE) {
                    break; // End of central directory entries
                }
                
                buffer.getShort(); // Skip versionMadeBy
                buffer.getShort(); // Skip versionNeeded
                buffer.getShort(); // Skip flags
                int compressionMethod = buffer.getShort() & 0xFFFF;
                buffer.getShort(); // Skip lastModTime
                buffer.getShort(); // Skip lastModDate
                long crc32 = buffer.getInt() & 0xFFFFFFFFL;
                long compressedSize = buffer.getInt() & 0xFFFFFFFFL;
                long uncompressedSize = buffer.getInt() & 0xFFFFFFFFL;
                int fileNameLength = buffer.getShort() & 0xFFFF;
                int extraFieldLength = buffer.getShort() & 0xFFFF;
                int fileCommentLength = buffer.getShort() & 0xFFFF;
                buffer.getShort(); // Skip diskNumber
                buffer.getShort(); // Skip internalAttributes
                long externalAttributes = buffer.getInt() & 0xFFFFFFFFL;
                long localHeaderOffset = buffer.getInt() & 0xFFFFFFFFL;
                
                // Read file name
                byte[] fileNameBytes = new byte[fileNameLength];
                buffer.get(fileNameBytes);
                String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
                
                // Handle ZIP64 extra field if central directory has 0xFFFFFFFF markers
                long actualCompressedSize = compressedSize;
                long actualUncompressedSize = uncompressedSize;
                long actualLocalHeaderOffset = localHeaderOffset;
                
                if (hasZip64Markers(compressedSize, uncompressedSize, localHeaderOffset)) {
                    logger.debug("ZIP64 extra field detected for file: {}", fileName);
                    // Parse ZIP64 extra field to get actual 64-bit values
                    if (extraFieldLength > 0) {
                        byte[] extraFieldData = new byte[extraFieldLength];
                        buffer.get(extraFieldData);
                        
                        // Parse ZIP64 extra field
                        ByteBuffer extraBuffer = ByteBuffer.wrap(extraFieldData);
                        extraBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
                        
                        while (extraBuffer.hasRemaining()) {
                            if (extraBuffer.remaining() < 4) break;
                            
                            int headerId = extraBuffer.getShort() & 0xFFFF;
                            int dataSize = extraBuffer.getShort() & 0xFFFF;
                            
                            if (headerId == 0x0001) { // ZIP64 extended information extra field
                                logger.debug("Found ZIP64 extra field, data size: {}", dataSize);
                                
                                if (uncompressedSize == ZIP64_MARKER) {
                                    actualUncompressedSize = extraBuffer.getLong();
                                    logger.debug("Uncompressed size: {}", actualUncompressedSize);
                                }
                                if (compressedSize == ZIP64_MARKER) {
                                    actualCompressedSize = extraBuffer.getLong();
                                    logger.debug("Compressed size: {}", actualCompressedSize);
                                }
                                if (localHeaderOffset == ZIP64_MARKER) {
                                    actualLocalHeaderOffset = extraBuffer.getLong();
                                    logger.debug("Local header offset: {}", actualLocalHeaderOffset);
                                }
                                break;
                            } else {
                                // Skip this extra field
                                extraBuffer.position(extraBuffer.position() + dataSize);
                            }
                        }
                    }
                } else {
                    // Skip extra field if not ZIP64
                    buffer.position(buffer.position() + extraFieldLength);
                }
                
                // Skip file comment
                buffer.position(buffer.position() + fileCommentLength);
                
                // Check if this is a directory
                boolean isDirectory = fileName.endsWith("/") || 
                                    (externalAttributes & 0x10) != 0; // Directory flag in external attributes
                
                entries.put(fileName, new ZipEntry(fileName, actualLocalHeaderOffset, actualCompressedSize, 
                    actualUncompressedSize, compressionMethod, crc32, isDirectory, fileNameLength, extraFieldLength));
            }
            
            return entries;
        }

        /**
         * Fetches a range of bytes from the remote file.
         */
        private CompletableFuture<byte[]> fetchRange(HttpClient client, String basicAuth, long start, long end) {
            String rangeHeader = "bytes=" + start + "-" + end;
            logger.debug("HTTP Range request: {} (HTTP {})", rangeHeader, client.version());
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Range", rangeHeader)
                .header("User-Agent", "jZipBrowser/1.0.0")
                .header("Accept", "*/*");
            
            if (basicAuth != null) {
                requestBuilder.header("Authorization", "Basic " + basicAuth);
            }
            
            HttpRequest request = requestBuilder.build();
            
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    logger.debug("HTTP Range response: {} (Server HTTP version: {}, Content-Length: {})", 
                                response.statusCode(), response.version(), 
                                response.headers().firstValue("Content-Length").orElse("unknown"));
                    
                    if (response.statusCode() == 206) { // Partial Content
                        logger.debug("Server supports HTTP Range requests (206 Partial Content)");
                        return response.body();
                    } else if (response.statusCode() == 200) {
                        // Server doesn't support range requests - fail fast
                        logger.error("Server doesn't support HTTP Range requests (returned 200 instead of 206)");
                        throw new RuntimeException("Server doesn't support HTTP Range requests (returned 200 instead of 206). " +
                                "This library requires servers that support HTTP Range requests for efficient ZIP file browsing.");
                    } else {
                        throw new RuntimeException("HTTP request failed with status: " + response.statusCode());
                    }
                });
        }
    }

    /**
     * Helper class to hold End of Central Directory record data.
     */
    private static class EndOfCentralDirectory {
        private final long centralDirectoryOffset;
        private final long centralDirectorySize;

        public EndOfCentralDirectory(long centralDirectoryOffset, long centralDirectorySize, int totalEntries) {
            this.centralDirectoryOffset = centralDirectoryOffset;
            this.centralDirectorySize = centralDirectorySize;
        }

        public long getCentralDirectoryOffset() {
            return centralDirectoryOffset;
        }

        public long getCentralDirectorySize() {
            return centralDirectorySize;
        }

    }
}

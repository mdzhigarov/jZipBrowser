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

/**
 * Represents a file entry within a ZIP archive.
 * Contains all the metadata needed to locate and extract the file.
 */
public class ZipEntry {
    private final String name;
    private final long localHeaderOffset;
    private final long compressedSize;
    private final long uncompressedSize;
    private final int compressionMethod;
    private final long crc32;
    private final boolean isDirectory;
    private final int fileNameLength;
    private final int extraFieldLength;

    /**
     * Creates a new ZipEntry with the specified metadata.
     *
     * @param name The name of the file in the ZIP archive
     * @param localHeaderOffset The offset to the local file header
     * @param compressedSize The size of the compressed data
     * @param uncompressedSize The size of the uncompressed data
     * @param compressionMethod The compression method used (0 = stored, 8 = deflated)
     * @param crc32 The CRC32 checksum of the uncompressed data
     * @param isDirectory Whether this entry represents a directory
     * @param fileNameLength The length of the file name in bytes
     * @param extraFieldLength The length of the extra field in bytes
     */
    public ZipEntry(String name, long localHeaderOffset, long compressedSize, 
                   long uncompressedSize, int compressionMethod, long crc32, boolean isDirectory,
                   int fileNameLength, int extraFieldLength) {
        this.name = name;
        this.localHeaderOffset = localHeaderOffset;
        this.compressedSize = compressedSize;
        this.uncompressedSize = uncompressedSize;
        this.compressionMethod = compressionMethod;
        this.crc32 = crc32;
        this.isDirectory = isDirectory;
        this.fileNameLength = fileNameLength;
        this.extraFieldLength = extraFieldLength;
    }

    /**
     * @return The name of the file in the ZIP archive
     */
    public String getName() {
        return name;
    }

    /**
     * @return The offset to the local file header from the beginning of the ZIP file
     */
    public long getLocalHeaderOffset() {
        return localHeaderOffset;
    }

    /**
     * @return The size of the compressed data in bytes
     */
    public long getCompressedSize() {
        return compressedSize;
    }

    /**
     * @return The size of the uncompressed data in bytes
     */
    public long getUncompressedSize() {
        return uncompressedSize;
    }

    /**
     * @return The compression method (0 = stored, 8 = deflated)
     */
    public int getCompressionMethod() {
        return compressionMethod;
    }

    /**
     * @return The CRC32 checksum of the uncompressed data
     */
    public long getCrc32() {
        return crc32;
    }

    /**
     * @return Whether this entry represents a directory
     */
    public boolean isDirectory() {
        return isDirectory;
    }

    /**
     * @return Whether this entry uses compression (compression method 8 = deflated)
     */
    public boolean isCompressed() {
        return compressionMethod == 8;
    }

    /**
     * @return The length of the file name in bytes
     */
    public int getFileNameLength() {
        return fileNameLength;
    }

    /**
     * @return The length of the extra field in bytes
     */
    public int getExtraFieldLength() {
        return extraFieldLength;
    }

    @Override
    public String toString() {
        return String.format("ZipEntry{name='%s', offset=%d, compressedSize=%d, uncompressedSize=%d, compressionMethod=%d, isDirectory=%s}",
                name, localHeaderOffset, compressedSize, uncompressedSize, compressionMethod, isDirectory);
    }
}


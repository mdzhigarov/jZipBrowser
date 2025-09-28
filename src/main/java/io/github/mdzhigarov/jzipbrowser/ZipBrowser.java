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

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * An interface for browsing and extracting files from a remote ZIP archive
 * without downloading the entire file.
 */
public interface ZipBrowser extends AutoCloseable {

    /**
     * Asynchronously retrieves a list of all file entries in the remote ZIP archive.
     *
     * @return A CompletableFuture that, upon completion, holds a list of file names.
     */
    CompletableFuture<List<String>> listFiles();

    /**
     * Asynchronously retrieves a specific file from the remote ZIP archive as an InputStream.
     * The file is fetched on-demand using HTTP Range requests and decompressed.
     *
     * @param fileName The exact name of the file inside the ZIP archive (e.g., "data/metadata.yml").
     * @return A CompletableFuture that, upon completion, holds an Optional containing an
     * InputStream for the requested file's uncompressed data. The Optional will be
     * empty if the file is not found.
     */
    CompletableFuture<Optional<InputStream>> getFile(String fileName);

    /**
     * Returns the total size of the remote ZIP file in bytes.
     * This value is fetched once during the initialization of the browser.
     *
     * @return The size of the file in bytes.
     */
    long getSize();

    /**
     * Factory method to obtain a builder for creating a ZipBrowser instance.
     *
     * @param url The URL of the remote ZIP file.
     * @return A new ZipBrowser.Builder instance.
     */
    static Builder newBuilder(URL url) {
        return new RemoteZipBrowser.Builder(url);
    }

    /**
     * A builder for configuring and creating a ZipBrowser instance.
     */
    interface Builder {
        /**
         * (Optional) Sets basic authentication credentials.
         *
         * @param username The username.
         * @param password The password.
         * @return This builder instance for chaining.
         */
        Builder withBasicAuth(String username, String password);

        /**
         * (Optional) Provide a custom HttpClient instance. If not provided, a default one is created.
         * @param client The HttpClient to use.
         * @return This builder instance for chaining.
         */
        Builder withHttpClient(java.net.http.HttpClient client);
        
        /**
         * Asynchronously initializes the ZipBrowser. This method performs the initial
         * network requests to fetch the file size and the ZIP central directory.
         *
         * @return A CompletableFuture that completes with the ready-to-use ZipBrowser instance.
         */
        CompletableFuture<ZipBrowser> build();
    }
}


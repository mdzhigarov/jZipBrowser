# jZipBrowser

A high-performance Java library for efficiently browsing and extracting individual files from large remote ZIP archives without downloading the entire file. Perfect for working with large ZIP files hosted on HTTP servers.

## Features

- 🚀 **Efficient Remote Access**: Uses HTTP Range requests to extract individual files without downloading the entire ZIP
- 📦 **ZIP64 Support**: Handles both standard ZIP files and large ZIP64 archives (>4GB)
- 🔄 **Asynchronous API**: Non-blocking operations using `CompletableFuture`
- 🏗️ **Builder Pattern**: Clean, fluent API for easy configuration
- 🔐 **Authentication**: Built-in support for HTTP Basic Authentication
- 📝 **Professional Logging**: SLF4J integration for debugging and monitoring
- 🧪 **Well Tested**: Comprehensive JUnit test suite
- ⚡ **Zero Dependencies**: Only requires SLF4J for logging (no other external dependencies)

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>com.jzipbrowser</groupId>
    <artifactId>jzipbrowser</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Basic Usage

```java
import com.jzipbrowser.ZipBrowser;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.io.InputStream;

// Create a ZipBrowser instance
URL zipUrl = new URL("https://example.com/large-archive.zip");
CompletableFuture<ZipBrowser> future = ZipBrowser.newBuilder(zipUrl).build();

// Wait for initialization
ZipBrowser browser = future.get();

// List all files in the archive
List<String> files = browser.listFiles().get();
System.out.println("Files in archive: " + files);

// Extract a specific file
Optional<InputStream> fileStream = browser.getFile("path/to/file.txt").get();
if (fileStream.isPresent()) {
    try (InputStream is = fileStream.get()) {
        // Read the file content
        byte[] content = is.readAllBytes();
        System.out.println("File content: " + new String(content));
    }
}

// Clean up
browser.close();
```

## Advanced Usage

### With Authentication

```java
URL zipUrl = new URL("https://secure-server.com/private-archive.zip");
ZipBrowser browser = ZipBrowser.newBuilder(zipUrl)
    .withBasicAuth("username", "password")
    .build()
    .get();
```

### With Custom HTTP Client

```java
import java.net.http.HttpClient;
import java.time.Duration;

HttpClient customClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(30))
    .build();

ZipBrowser browser = ZipBrowser.newBuilder(zipUrl)
    .withHttpClient(customClient)
    .withBasicAuth("user", "pass")
    .build()
    .get();
```

### Asynchronous Processing

```java
// Process multiple files asynchronously
List<String> files = browser.listFiles().get();

List<CompletableFuture<Optional<InputStream>>> futures = files.stream()
    .map(fileName -> browser.getFile(fileName))
    .collect(Collectors.toList());

// Wait for all files to be processed
CompletableFuture<Void> allFiles = CompletableFuture.allOf(
    futures.toArray(new CompletableFuture[0])
);

allFiles.thenRun(() -> {
    System.out.println("All files processed!");
}).get();
```

## API Reference

### ZipBrowser Interface

```java
public interface ZipBrowser extends AutoCloseable {
    // List all files in the archive
    CompletableFuture<List<String>> listFiles();
    
    // Get a specific file as InputStream
    CompletableFuture<Optional<InputStream>> getFile(String fileName);
    
    // Get the total size of the ZIP file
    long getSize();
    
    // Close the browser and release resources
    void close();
}
```

### Builder Pattern

```java
public static class Builder {
    // Create a new builder with the ZIP file URL
    public static Builder newBuilder(URL url);
    
    // Add HTTP Basic Authentication
    public Builder withBasicAuth(String username, String password);
    
    // Use a custom HttpClient
    public Builder withHttpClient(HttpClient client);
    
    // Build the ZipBrowser instance (returns CompletableFuture)
    public CompletableFuture<ZipBrowser> build();
}
```

## Supported ZIP Formats

### Standard ZIP Files
- Files up to 4GB
- Standard compression methods (stored, deflated)
- Traditional ZIP file structure

### ZIP64 Files
- Files larger than 4GB
- 64-bit file sizes and offsets
- Extended ZIP64 format support
- Automatic detection and handling

## Error Handling

The library provides comprehensive error handling for common scenarios:

```java
try {
    ZipBrowser browser = ZipBrowser.newBuilder(zipUrl)
        .withBasicAuth("user", "pass")
        .build()
        .get(30, TimeUnit.SECONDS);
    
    List<String> files = browser.listFiles().get();
    
} catch (TimeoutException e) {
    System.err.println("Request timed out");
} catch (ExecutionException e) {
    if (e.getCause() instanceof IOException) {
        System.err.println("Network error: " + e.getCause().getMessage());
    } else if (e.getCause() instanceof RuntimeException) {
        String message = e.getCause().getMessage();
        if (message != null && message.contains("Server doesn't support HTTP Range requests")) {
            System.err.println("Server compatibility error: " + message);
            System.err.println("This server doesn't support HTTP Range requests, which are required for efficient ZIP browsing.");
        } else {
            System.err.println("ZIP parsing error: " + message);
        }
    }
} catch (InterruptedException e) {
    System.err.println("Operation was interrupted");
}
```

## Performance Considerations

### HTTP Range Requests
- The library uses HTTP Range requests to fetch only the necessary parts of the ZIP file
- Central directory is parsed first to build an index of all files
- Individual files are fetched on-demand using precise byte ranges

### Memory Efficiency
- Only the central directory is kept in memory
- File contents are streamed directly from HTTP responses
- No temporary files are created on disk

### Network Optimization
- HTTP/1.1 support with persistent connections
- Custom HttpClient support for advanced configuration
- Efficient HTTP Range requests for partial content retrieval

## Logging

The library uses SLF4J for logging. Configure your logging framework to see detailed information:

```xml
<!-- Logback configuration example -->
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <logger name="com.jzipbrowser" level="DEBUG"/>
    
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

## Testing

Run the test suite:

```bash
# Run all tests
mvn test

# Run only unit tests (excluding integration tests)
mvn test -Dtest="!*IntegrationTest"

# Run with verbose output
mvn test -X
```

### Test Categories

- **Unit Tests**: Test individual components in isolation
- **Integration Tests**: Test with real HTTP endpoints (requires environment variables)

## Building from Source

```bash
# Clone the repository
git clone https://github.com/your-org/jZipBrowser.git
cd jZipBrowser

# Build the project
mvn clean compile

# Run tests
mvn test

# Create JAR file
mvn package
```

## Requirements

- **Java**: 11 or higher
- **Maven**: 3.6 or higher (for building)
- **Network**: HTTP/1.1 support with Range request capability

## Limitations

- **Requires HTTP/1.1 server support for Range requests** - The library will fail fast if the server doesn't support HTTP Range requests (returns 200 instead of 206)
- ZIP files must have a valid central directory
- Some ZIP files with unusual structures may not be supported
- Large ZIP files with many entries may take longer to initialize

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Changelog

### Version 1.0.0
- Initial release
- Support for standard ZIP and ZIP64 formats
- HTTP Range request implementation
- Asynchronous API with CompletableFuture
- Builder pattern for configuration
- Comprehensive test suite
- SLF4J logging integration

## Support

For questions, issues, or contributions, please visit the [GitHub repository](https://github.com/your-org/jZipBrowser) or contact the maintainers.

---

**jZipBrowser** - Efficiently browse remote ZIP archives without downloading the entire file! 🚀
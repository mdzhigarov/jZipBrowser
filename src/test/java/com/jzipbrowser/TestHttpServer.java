package com.jzipbrowser;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Test HTTP server that supports HTTP/1.1 and Range requests for testing jZipBrowser.
 */
public class TestHttpServer {
    private static final Logger logger = LoggerFactory.getLogger(TestHttpServer.class);
    
    private HttpServer server;
    private int port;
    private final Map<String, byte[]> files = new HashMap<>();
    
    public TestHttpServer() throws IOException {
        // Find an available port
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        
        // Set HTTP/1.1
        server.setExecutor(Executors.newCachedThreadPool());
        
        // Add handler for serving files
        server.createContext("/", new FileHandler());
        
        logger.info("Test HTTP server created on port {}", port);
    }
    
    public void start() {
        server.start();
        logger.info("Test HTTP server started on port {}", port);
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("Test HTTP server stopped");
        }
    }
    
    public int getPort() {
        return port;
    }
    
    public String getBaseUrl() {
        return "http://localhost:" + port;
    }
    
    public void addFile(String path, byte[] content) {
        files.put(path, content);
        logger.debug("Added file {} with {} bytes", path, content.length);
    }
    
    public void addFile(String path, Path filePath) throws IOException {
        byte[] content = Files.readAllBytes(filePath);
        addFile(path, content);
    }
    
    private class FileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestMethod = exchange.getRequestMethod();
            String requestPath = exchange.getRequestURI().getPath();
            
            logger.debug("HTTP {} request for path: {}", requestMethod, requestPath);
            
            // Set HTTP/1.1 headers
            exchange.getResponseHeaders().set("Server", "TestHttpServer/1.1");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            
            if ("GET".equals(requestMethod)) {
                handleGetRequest(exchange, requestPath);
            } else if ("HEAD".equals(requestMethod)) {
                handleHeadRequest(exchange, requestPath);
            } else {
                // Method not allowed
                exchange.sendResponseHeaders(405, 0);
                exchange.getResponseBody().close();
            }
        }
        
        private void handleHeadRequest(HttpExchange exchange, String requestPath) throws IOException {
            byte[] fileContent = files.get(requestPath);
            
            if (fileContent == null) {
                // File not found
                exchange.sendResponseHeaders(404, 0);
                exchange.getResponseBody().close();
                return;
            }
            
            // Set response headers for HEAD request
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(fileContent.length));
            exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            
            // Send 200 OK with no body
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
            
            logger.debug("Served HEAD request: {} bytes", fileContent.length);
        }
        
        private void handleGetRequest(HttpExchange exchange, String requestPath) throws IOException {
            byte[] fileContent = files.get(requestPath);
            
            if (fileContent == null) {
                // File not found
                exchange.sendResponseHeaders(404, 0);
                exchange.getResponseBody().close();
                return;
            }
            
            // Check for Range request
            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                handleRangeRequest(exchange, fileContent, rangeHeader);
            } else {
                // Full file request
                handleFullFileRequest(exchange, fileContent);
            }
        }
        
        private void handleRangeRequest(HttpExchange exchange, byte[] fileContent, String rangeHeader) throws IOException {
            try {
                // Parse range header: "bytes=start-end"
                String range = rangeHeader.substring(6); // Remove "bytes="
                String[] parts = range.split("-");
                
                long start = 0;
                long end = fileContent.length - 1;
                
                if (parts.length >= 1 && !parts[0].isEmpty()) {
                    start = Long.parseLong(parts[0]);
                }
                
                if (parts.length >= 2 && !parts[1].isEmpty()) {
                    end = Long.parseLong(parts[1]);
                }
                
                // Validate range
                if (start < 0 || end >= fileContent.length || start > end) {
                    exchange.sendResponseHeaders(416, 0); // Range Not Satisfiable
                    exchange.getResponseBody().close();
                    return;
                }
                
                // Calculate content length
                long contentLength = end - start + 1;
                byte[] rangeContent = new byte[(int) contentLength];
                System.arraycopy(fileContent, (int) start, rangeContent, 0, (int) contentLength);
                
                // Set response headers for partial content
                exchange.getResponseHeaders().set("Content-Range", 
                    String.format("bytes %d-%d/%d", start, end, fileContent.length));
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(contentLength));
                exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
                
                // Send 206 Partial Content
                exchange.sendResponseHeaders(206, contentLength);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(rangeContent);
                }
                
                logger.debug("Served range request: bytes {}-{} of {} ({} bytes)", 
                    start, end, fileContent.length, contentLength);
                
            } catch (NumberFormatException e) {
                // Invalid range format
                exchange.sendResponseHeaders(400, 0);
                exchange.getResponseBody().close();
            }
        }
        
        private void handleFullFileRequest(HttpExchange exchange, byte[] fileContent) throws IOException {
            // Set response headers
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(fileContent.length));
            exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            
            // Send 200 OK
            exchange.sendResponseHeaders(200, fileContent.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileContent);
            }
            
            logger.debug("Served full file request: {} bytes", fileContent.length);
        }
    }
}

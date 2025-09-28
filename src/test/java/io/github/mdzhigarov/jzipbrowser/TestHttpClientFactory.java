package io.github.mdzhigarov.jzipbrowser;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * Factory for creating HTTP clients for testing purposes.
 * This class provides HTTP clients that skip TLS verification for integration tests.
 */
public class TestHttpClientFactory {
    
    /**
     * Creates an HTTP client that trusts all certificates (for testing only).
     * This should NEVER be used in production code.
     * 
     * @return HttpClient that skips TLS verification
     */
    public static HttpClient createTrustAllHttpClient() {
        try {
            // Create a trust manager that accepts all certificates
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    
                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Trust all client certificates
                    }
                    
                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Trust all server certificates
                    }
                }
            };
            
            // Create SSL context with the trust-all manager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            
            // Create HTTP client with custom SSL context
            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
                    
        } catch (Exception e) {
            throw new RuntimeException("Failed to create trust-all HTTP client", e);
        }
    }
    
    /**
     * Creates a standard HTTP client for normal testing.
     * 
     * @return Standard HttpClient
     */
    public static HttpClient createStandardHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }
}

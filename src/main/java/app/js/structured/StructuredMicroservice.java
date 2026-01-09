package app.js.structured;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class StructuredMicroservice {
    private static final Logger logger = LoggerFactory.getLogger(StructuredMicroservice.class);
    private static final int PORT = 8085;
    
    private final BusinessService businessService = new BusinessService();
    private final AtomicLong requestCounter = new AtomicLong(0);
    
    public static void main(String[] args) throws IOException {
        new StructuredMicroservice().start();
    }
    
    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/user/profile", this::handleUserProfile);
        server.createContext("/user/dashboard", this::handleUserDashboard);
        server.createContext("/services/aggregate", this::handleServiceAggregation);
        server.createContext("/cache/data", this::handleCachedData);
        server.createContext("/data/with-fallback", this::handleDataWithFallback);
        server.createContext("/retry/operation", this::handleRetryOperation);
        server.createContext("/protected/service", this::handleProtectedService);
        server.createContext("/timed/operation", this::handleTimedOperation);
        server.createContext("/order/process", this::handleOrderProcessing);

        server.createContext("/health", exchange -> {
            sendResponse(exchange, "Clean Structured Microservice is running!");
        });
        
        server.start();
        logger.info(" Clean Structured Microservice started on port {}", PORT);
        logger.info("Endpoints:");
        logger.info("  GET /user/profile?userId=123    - Parallel user data fetching");
        logger.info("  GET /user/dashboard?userId=123  - Three-way parallel aggregation");
        logger.info("  GET /services/aggregate         - Multi-service aggregation");
        logger.info("  GET /cache/data?key=test        - First successful response");
        logger.info("  GET /data/with-fallback?key=test - Primary with fallback");
        logger.info("  GET /retry/operation?op=test    - Retry pattern");
        logger.info("  GET /protected/service?req=test - Circuit breaker");
        logger.info("  GET /timed/operation?op=test    - Timeout handling");
        logger.info("  GET /order/process?orderId=123  - Complex workflow");
        logger.info("  GET /health                     - Health check");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Clean Structured Microservice...");
            server.stop(2);
        }));
    }
    
    private void handleUserProfile(HttpExchange exchange) {
        handleRequest(exchange, "USER_PROFILE", () -> {
            String userId = getQueryParam(exchange, "userId", "default-user");
            return businessService.fetchUserProfile(userId);
        });
    }
    
    private void handleUserDashboard(HttpExchange exchange) {
        handleRequest(exchange, "USER_DASHBOARD", () -> {
            String userId = getQueryParam(exchange, "userId", "default-user");
            return businessService.buildDashboard(userId);
        });
    }
    
    private void handleServiceAggregation(HttpExchange exchange) {
        handleRequest(exchange, "SERVICE_AGGREGATION", () -> {
            return businessService.aggregateServices();
        });
    }
    
    private void handleCachedData(HttpExchange exchange) {
        handleRequest(exchange, "CACHED_DATA", () -> {
            String key = getQueryParam(exchange, "key", "default-key");
            return businessService.getCachedData(key);
        });
    }
    
    private void handleDataWithFallback(HttpExchange exchange) {
        handleRequest(exchange, "DATA_WITH_FALLBACK", () -> {
            String key = getQueryParam(exchange, "key", "default-key");
            return businessService.getDataWithFallback(key);
        });
    }
    
    private void handleRetryOperation(HttpExchange exchange) {
        handleRequest(exchange, "RETRY_OPERATION", () -> {
            String operation = getQueryParam(exchange, "op", "default-operation");
            return businessService.performRetryableOperation(operation);
        });
    }
    
    private void handleProtectedService(HttpExchange exchange) {
        handleRequest(exchange, "PROTECTED_SERVICE", () -> {
            String request = getQueryParam(exchange, "req", "default-request");
            return businessService.callProtectedService(request);
        });
    }
    
    private void handleTimedOperation(HttpExchange exchange) {
        handleRequest(exchange, "TIMED_OPERATION", () -> {
            String operation = getQueryParam(exchange, "op", "default-operation");
            return businessService.performTimedOperation(operation);
        });
    }
    
    private void handleOrderProcessing(HttpExchange exchange) {
        handleRequest(exchange, "ORDER_PROCESSING", () -> {
            String orderId = getQueryParam(exchange, "orderId", "default-order");
            return businessService.processOrder(orderId);
        });
    }
    
    private void handleRequest(HttpExchange exchange, String endpoint, RequestHandler handler) {
        long requestId = requestCounter.incrementAndGet();
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        
        MDC.put("traceId", traceId);
        MDC.put("requestId", String.valueOf(requestId));
        MDC.put("endpoint", endpoint);
        
        try {
            logger.info("Processing request: {}", endpoint);
            long startTime = System.currentTimeMillis();
            
            String result = handler.handle();
            
            long duration = System.currentTimeMillis() - startTime;
            String response = String.format("[%s] %s (Duration: %dms, Thread: %s)", 
                traceId, result, duration, Thread.currentThread().getName());
            
            logger.info("Request completed successfully in {}ms", duration);
            sendResponse(exchange, response);
            
        } catch (Exception e) {
            logger.error("Request failed: {}", e.getMessage(), e);
            sendErrorResponse(exchange, String.format("[%s] Error: %s", traceId, e.getMessage()));
        } finally {
            MDC.clear();
        }
    }
    
    @FunctionalInterface
    interface RequestHandler {
        String handle() throws Exception;
    }
    
    private String getQueryParam(HttpExchange exchange, String param, String defaultValue) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return defaultValue;
        
        return java.util.Arrays.stream(query.split("&"))
            .filter(p -> p.startsWith(param + "="))
            .map(p -> p.substring(param.length() + 1))
            .findFirst()
            .orElse(defaultValue);
    }
    
    private void sendResponse(HttpExchange exchange, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
    
    private void sendErrorResponse(HttpExchange exchange, String error) {
        try {
            exchange.sendResponseHeaders(500, error.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(error.getBytes());
            }
        } catch (IOException e) {
            logger.error("Failed to send error response", e);
        }
    }
}
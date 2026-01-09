package app.js.microservices;

import app.js.client.AsyncHttpClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class AdvancedStructuredConcurrencyMicroservice {
    private static final Logger logger = LoggerFactory.getLogger(AdvancedStructuredConcurrencyMicroservice.class);
    
    public static final int PORT = 8082;
    private static final String LARGE_FILE = "advanced_microservice_test_file.txt";
    private static final AtomicLong requestCounter = new AtomicLong(0);
    private static final AtomicLong totalRequests = new AtomicLong(0);
    private static final AtomicLong totalResponseTime = new AtomicLong(0);
    private static final AtomicLong activeRequests = new AtomicLong(0);
    private static final AtomicLong timeoutCount = new AtomicLong(0);

    private static final ConcurrentServiceLayer serviceLayer = new ConcurrentServiceLayer();
    private static final AsyncHttpClient asyncHttpClient = new AsyncHttpClient();

    private static final Runtime runtime = Runtime.getRuntime();
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private static final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
    private static volatile double cpuUsage = 0.0;
    
    static void main(String[] args) throws IOException {
        createTestFile();
        startMetricsLogger();
        
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/structured/block", exchange -> handleRequest(exchange, "STRUCTURED_BLOCK", serviceLayer::structuredDbCall));
        server.createContext("/structured/file", exchange -> handleRequest(exchange, "STRUCTURED_FILE", serviceLayer::structuredFileOperation));
        server.createContext("/structured/aggregate", exchange -> handleRequest(exchange, "STRUCTURED_AGGREGATE", serviceLayer::structuredAggregateServices));
        server.createContext("/timeout/short", exchange -> handleRequest(exchange, "TIMEOUT_SHORT", serviceLayer::shortTimeoutExample));
        server.createContext("/timeout/graceful", exchange -> handleRequest(exchange, "TIMEOUT_GRACEFUL", serviceLayer::gracefulTimeoutExample));
        server.createContext("/deadline/strict", exchange -> handleRequest(exchange, "DEADLINE_STRICT", serviceLayer::strictDeadlineExample));
        server.createContext("/async/multi-service", exchange -> handleRequest(exchange, "ASYNC_MULTI_SERVICE", serviceLayer::asyncMultiServiceCall));
        server.createContext("/async/failover", exchange -> handleRequest(exchange, "ASYNC_FAILOVER", serviceLayer::asyncFailoverCall));
        server.createContext("/async/race", exchange -> handleRequest(exchange, "ASYNC_RACE", serviceLayer::asyncRaceCondition));
        server.createContext("/service/pipeline", exchange -> handleRequest(exchange, "SERVICE_PIPELINE", serviceLayer::servicePipeline));
        server.createContext("/service/circuit-breaker", exchange -> handleRequest(exchange, "CIRCUIT_BREAKER", serviceLayer::circuitBreakerExample));
        server.createContext("/service/retry", exchange -> handleRequest(exchange, "RETRY_PATTERN", serviceLayer::retryPatternExample));
        server.createContext("/pattern/scatter-gather", exchange -> handleRequest(exchange, "SCATTER_GATHER", serviceLayer::scatterGatherPattern));
        server.createContext("/pattern/bulkhead", exchange -> handleRequest(exchange, "BULKHEAD", serviceLayer::bulkheadPattern));

        server.createContext("/metrics", exchange -> {
            String metrics = generateMetrics();
            sendResponse(exchange, metrics);
        });
        
        server.createContext("/health", exchange -> sendResponse(exchange, "Advanced Structured Concurrency Microservice is running!"));
        
        server.start();
        logger.info(" Advanced Structured Concurrency Microservice started on port " + PORT);
        logger.info("Features:");
        logger.info("   Structured variants:");
        logger.info("    GET /structured/block     - Structured DB call");
        logger.info("    GET /structured/file      - Structured file operation");
        logger.info("    GET /structured/aggregate - Structured service aggregation");
        logger.info("   Timeout & deadline:");
        logger.info("    GET /timeout/short        - Short timeout example");
        logger.info("    GET /timeout/graceful     - Graceful timeout handling");
        logger.info("    GET /deadline/strict      - Strict deadline enforcement");
        logger.info("   Async HTTP calls:");
        logger.info("    GET /async/multi-service  - Multi-service async calls");
        logger.info("    GET /async/failover       - Failover async pattern");
        logger.info("    GET /async/race           - Race condition handling");
        logger.info("   Service layer patterns:");
        logger.info("    GET /service/pipeline     - Service pipeline");
        logger.info("    GET /service/circuit-breaker - Circuit breaker pattern");
        logger.info("    GET /service/retry        - Retry pattern");
        logger.info("   Advanced patterns:");
        logger.info("    GET /pattern/scatter-gather - Scatter-gather pattern");
        logger.info("    GET /pattern/bulkhead     - Bulkhead pattern");
        logger.info("   Monitoring:");
        logger.info("    GET /metrics              - Performance metrics");
        logger.info("    GET /health               - Health check");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("\nShutting down Advanced Structured Concurrency Microservice...");
            server.stop(2);
            asyncHttpClient.close();
            cleanupTestFile();
        }));
    }
    
    private static void handleRequest(HttpExchange exchange, String endpoint, RequestHandler handler) {
        long requestId = requestCounter.incrementAndGet();
        long startTime = System.currentTimeMillis();
        activeRequests.incrementAndGet();
        
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", traceId);
        MDC.put("requestId", String.valueOf(requestId));
        MDC.put("endpoint", endpoint);
        
        try {
            logger.info("Processing request: {}", endpoint);
            
            String result = handler.handle();
            long duration = System.currentTimeMillis() - startTime;
            totalRequests.incrementAndGet();
            totalResponseTime.addAndGet(duration);
            
            String response = String.format("[%s] %s (Duration: %dms, Thread: %s, Request: #%d)", 
                traceId, result, duration, Thread.currentThread().getName(), requestId);
            
            logger.info("Request completed successfully in {}ms", duration);
            sendResponse(exchange, response);
            
        } catch (TimeoutException e) {
            timeoutCount.incrementAndGet();
            long duration = System.currentTimeMillis() - startTime;
            totalResponseTime.addAndGet(duration);
            
            String response = String.format("[%s] Request timed out after %dms", traceId, duration);
            logger.warn("Request timed out: {}", e.getMessage());
            sendErrorResponse(exchange, response);
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            totalResponseTime.addAndGet(duration);
            
            String response = String.format("[%s] Error: %s", traceId, e.getMessage());
            logger.error("Request failed: {}", e.getMessage(), e);
            sendErrorResponse(exchange, response);
            
        } finally {
            activeRequests.decrementAndGet();
            MDC.clear();
        }
    }
    
    @FunctionalInterface
    interface RequestHandler {
        String handle() throws Exception;
    }
    
    private static void startMetricsLogger() {
        try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)) {
            scheduler.scheduleAtFixedRate(() -> {
                updateCpuUsage();
                System.out.printf("[METRICS] Active: %d, Total: %d, Timeouts: %d, Avg Response: %.2fms, CPU: %.2f%%, Memory: %.2fMB%n",
                        activeRequests.get(),
                        totalRequests.get(),
                        timeoutCount.get(),
                        totalRequests.get() > 0 ? (double) totalResponseTime.get() / totalRequests.get() : 0,
                        cpuUsage,
                        (runtime.totalMemory() - runtime.freeMemory()) / 1024.0 / 1024.0
                );
            }, 5, 5, TimeUnit.SECONDS);
        }
    }
    
    private static void updateCpuUsage() {
        try {
            com.sun.management.OperatingSystemMXBean osBean = 
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            cpuUsage = osBean.getCpuLoad() * 100;
        } catch (Exception e) {
            cpuUsage = 0.0;
        }
    }
    
    private static String generateMetrics() {
        updateCpuUsage();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        
        return String.format("""
            Advanced Structured Concurrency Microservice Metrics:
            ====================================================
            Active Requests: %d
            Total Requests: %d
            Timeout Count: %d
            Average Response Time: %.2fms
            CPU Usage: %.2f%%
            Memory Usage: %.2fMB / %.2fMB
            JVM Uptime: %d seconds
            Thread Type: Virtual Threads + Structured Concurrency
            Features: Timeouts, Deadlines, Async HTTP, Service Layers
            """,
            activeRequests.get(),
            totalRequests.get(),
            timeoutCount.get(),
            totalRequests.get() > 0 ? (double)totalResponseTime.get() / totalRequests.get() : 0,
            cpuUsage,
            usedMemory / 1024.0 / 1024.0,
            runtime.totalMemory() / 1024.0 / 1024.0,
            runtimeBean.getUptime() / 1000
        );
    }
    
    private static void createTestFile() throws IOException {
        Path filePath = Paths.get(LARGE_FILE);
        if (!Files.exists(filePath)) {
            logger.info("Creating test file: " + LARGE_FILE);
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < 20_000; i++) {
                content.append("Advanced structured concurrency test line ")
                       .append(i + 1)
                       .append(" with timeout and deadline features.\n");
            }
            Files.writeString(filePath, content.toString());
            logger.info("Test file created successfully.");
        }
    }
    
    private static void cleanupTestFile() {
        try {
            Files.deleteIfExists(Paths.get(LARGE_FILE));
            logger.info("Test file cleaned up.");
        } catch (IOException e) {
            System.err.println("Failed to clean up test file: " + e.getMessage());
        }
    }
    
    private static void sendResponse(HttpExchange exchange, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
    
    private static void sendErrorResponse(HttpExchange exchange, String error) {
        try {
            exchange.sendResponseHeaders(500, error.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(error.getBytes());
            }
        } catch (IOException e) {
            System.err.println("Failed to send error response: " + e.getMessage());
        }
    }
}
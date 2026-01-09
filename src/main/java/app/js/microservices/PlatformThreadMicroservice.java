package app.js.microservices;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class PlatformThreadMicroservice {
    private static final Logger logger = LoggerFactory.getLogger(PlatformThreadMicroservice.class);
    private static final int PORT = 8081;
    private static final int THREAD_POOL_SIZE = 200;
    private static final String LARGE_FILE = "microservice_test_file.txt";
    private static final AtomicLong requestCounter = new AtomicLong(0);
    private static final AtomicLong totalRequests = new AtomicLong(0);
    private static final AtomicLong totalResponseTime = new AtomicLong(0);
    private static final AtomicLong activeRequests = new AtomicLong(0);
    
    private static final Runtime runtime = Runtime.getRuntime();
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private static final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
    private static final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private static volatile double cpuUsage = 0.0;
    
    static void main(String[] args) throws IOException {
        createTestFile();
        startMetricsLogger();
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(executor);
        server.createContext("/compute", exchange -> handleRequest(exchange, "COMPUTE", () -> {

            long result = 0;
            for (int i = 2; i <= 50_000; i++) {
                if (isPrime(i)) {
                    result += i;
                }
            }
            return "CPU Task completed. Result: " + result;
        }));

        server.createContext("/block", exchange -> {
            handleRequest(exchange, "BLOCK", () -> {
                try {
                    Thread.sleep(300);
                    return "DB call completed";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted", e);
                }
            });
        });

        server.createContext("/file", exchange -> {
            handleRequest(exchange, "FILE", () -> {
                try {
                    List<String> lines = Files.readAllLines(Paths.get(LARGE_FILE));
                    return "File read completed. Lines: " + lines.size();
                } catch (IOException e) {
                    throw new RuntimeException("File read error", e);
                }
            });
        });

        server.createContext("/metrics", exchange -> {
            String metrics = generateMetrics();
            sendResponse(exchange, metrics);
        });
        
        server.createContext("/health", exchange -> {
            sendResponse(exchange, "Platform Thread Microservice is running! Pool size: " + THREAD_POOL_SIZE);
        });
        
        server.start();
        logger.info(" Platform Thread Microservice started on port " + PORT);
        logger.info("Thread pool size: " + THREAD_POOL_SIZE);
        logger.info("Endpoints:");
        logger.info("  GET /compute - CPU-intensive task");
        logger.info("  GET /block   - Simulated DB call (300ms)");
        logger.info("  GET /file    - Large file read");
        logger.info("  GET /metrics - Performance metrics");
        logger.info("  GET /health  - Health check");
        logger.info("\nReady for wrk benchmarking!");
        logger.info("Example: wrk -t8 -c1000 -d30s http://localhost:8081/block");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("\nShutting down Platform Thread Microservice...");
            server.stop(2);
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            cleanupTestFile();
        }));
    }
    
    private static void handleRequest(HttpExchange exchange, String endpoint, RequestHandler handler) {
        long requestId = requestCounter.incrementAndGet();
        long startTime = System.currentTimeMillis();
        activeRequests.incrementAndGet();
        
        try {
            String result = handler.handle();
            long duration = System.currentTimeMillis() - startTime;
            
            totalRequests.incrementAndGet();
            totalResponseTime.addAndGet(duration);
            
            String response = String.format("%s (Duration: %dms, Thread: %s, Request: #%d)", 
                result, duration, Thread.currentThread().getName(), requestId);
            
            sendResponse(exchange, response);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            totalResponseTime.addAndGet(duration);
            sendErrorResponse(exchange, "Error: " + e.getMessage());
        } finally {
            activeRequests.decrementAndGet();
        }
    }
    
    @FunctionalInterface
    interface RequestHandler {
        String handle() throws Exception;
    }
    
    private static void startMetricsLogger() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            updateCpuUsage();
            System.out.printf("[METRICS] Active: %d, Total: %d, Avg Response: %.2fms, CPU: %.2f%%, Memory: %.2fMB, Threads: %d%n",
                activeRequests.get(),
                totalRequests.get(),
                totalRequests.get() > 0 ? (double)totalResponseTime.get() / totalRequests.get() : 0,
                cpuUsage,
                (runtime.totalMemory() - runtime.freeMemory()) / 1024.0 / 1024.0,
                threadBean.getThreadCount()
            );
        }, 5, 5, TimeUnit.SECONDS);
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
            Platform Thread Microservice Metrics:
            =====================================
            Active Requests: %d
            Total Requests: %d
            Average Response Time: %.2fms
            CPU Usage: %.2f%%
            Memory Usage: %.2fMB / %.2fMB
            Thread Pool Size: %d
            Active Threads: %d
            JVM Uptime: %d seconds
            Thread Type: Platform Threads
            """,
            activeRequests.get(),
            totalRequests.get(),
            totalRequests.get() > 0 ? (double)totalResponseTime.get() / totalRequests.get() : 0,
            cpuUsage,
            usedMemory / 1024.0 / 1024.0,
            runtime.totalMemory() / 1024.0 / 1024.0,
            THREAD_POOL_SIZE,
            threadBean.getThreadCount(),
            runtimeBean.getUptime() / 1000
        );
    }
    
    private static boolean isPrime(int n) {
        if (n <= 1) return false;
        if (n <= 3) return true;
        if (n % 2 == 0 || n % 3 == 0) return false;
        
        for (int i = 5; i * i <= n; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) {
                return false;
            }
        }
        return true;
    }
    
    private static void createTestFile() throws IOException {
        Path filePath = Paths.get(LARGE_FILE);
        if (!Files.exists(filePath)) {
            logger.info("Creating test file: " + LARGE_FILE);
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < 10_000; i++) {
                content.append("This is line ").append(i + 1)
                       .append(" of the microservice test file with some sample data for benchmarking.\n");
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
            System.err.println("Failed to cleanup test file: " + e.getMessage());
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
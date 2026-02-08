package app.js.microservices;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.management.OperatingSystemMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class VirtualThreadMicroservice {
    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadMicroservice.class);

    public static final int PORT = 8080;
    private static final String LARGE_FILE = "microservice_test_file.txt";
    private static final AtomicLong requestCounter = new AtomicLong(0);
    private static final AtomicLong totalRequests = new AtomicLong(0);
    private static final AtomicLong totalResponseTime = new AtomicLong(0);
    private static final AtomicLong activeRequests = new AtomicLong(0);

    private static final Runtime runtime = Runtime.getRuntime();
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private static final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
    private static volatile double cpuUsage = 0.0;

    static void main(String[] args) throws IOException {
        createTestFile();
        startMetricsLogger();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/compute", exchange -> handleRequest(exchange, "COMPUTE", () -> {
            long result = 0;
            for (int i = 2; i <= 50_000; i++) {
                if (isPrime(i)) {
                    result += i;
                }
            }
            return "CPU Task completed. Result: " + result;
        }));

        server.createContext("/block", exchange -> handleRequest(exchange, "BLOCK", () -> {
            try {
                Thread.sleep(300);
                return "DB call completed";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted", e);
            }
        }));

        server.createContext("/file", exchange -> handleRequest(exchange, "FILE", () -> {
            try {
                List<String> lines = Files.readAllLines(Paths.get(LARGE_FILE));
                return "File read completed. Lines: " + lines.size();
            } catch (IOException e) {
                throw new RuntimeException("File read error", e);
            }
        }));

        server.createContext("/aggregate", exchange -> handleRequest(exchange, "AGGREGATE", VirtualThreadMicroservice::aggregateWithStructuredConcurrency));
        server.createContext("/aggregate-old", exchange -> handleRequest(exchange, "AGGREGATE_OLD", VirtualThreadMicroservice::aggregateWithCompletableFuture));
        server.createContext("/first-success", exchange -> handleRequest(exchange, "FIRST_SUCCESS", VirtualThreadMicroservice::firstSuccessWithStructuredConcurrency));
        server.createContext("/aggregate-with-fallback", exchange -> handleRequest(exchange, "AGGREGATE_FALLBACK", VirtualThreadMicroservice::aggregateWithFallback));
        server.createContext("/multi-aggregate", exchange -> handleRequest(exchange, "MULTI_AGGREGATE", VirtualThreadMicroservice::multiServiceAggregation));
        server.createContext("/metrics", exchange -> {
            String metrics = generateMetrics();
            sendResponse(exchange, metrics);
        });

        server.createContext("/health", exchange -> sendResponse(exchange, "Virtual Thread Microservice is running!"));

        server.start();
        logger.info(" Virtual Thread Microservice started on port " + PORT);
        logger.info("Endpoints:");
        logger.info("  GET /compute           - CPU-intensive task");
        logger.info("  GET /block             - Simulated DB call (300ms)");
        logger.info("  GET /file              - Large file read");
        logger.info("  GET /aggregate         - StructuredTaskScope aggregate");
        logger.info("  GET /aggregate-old     - CompletableFuture aggregate");
        logger.info("  GET /first-success     - First successful response");
        logger.info("  GET /aggregate-with-fallback - With error handling");
        logger.info("  GET /multi-aggregate   - Multiple service aggregation");
        logger.info("  GET /metrics           - Performance metrics");
        logger.info("  GET /health            - Health check");
        logger.info("\nReady for wrk benchmarking!");
        logger.info("Example: wrk -t8 -c1000 -d30s http://localhost:8080/aggregate");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("\nShutting down Virtual Thread Microservice...");
            server.stop(2);
            cleanupTestFile();
        }));
    }

    private static String aggregateWithStructuredConcurrency() throws Exception {
        long startTime = System.currentTimeMillis();
        
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            var blockFuture = scope.fork(() -> fetchBlock());
            var fileFuture = scope.fork(() -> fetchFile());

            scope.join();

            long duration = System.currentTimeMillis() - startTime;
            String result = String.format("StructuredTaskScope Combined: %s | %s (Total: %dms)", 
                blockFuture.get(), fileFuture.get(), duration);
            
            return result;
        }
    }

    private static String aggregateWithCompletableFuture() throws Exception {
        long startTime = System.currentTimeMillis();
        
        CompletableFuture<String> blockFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return fetchBlock();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        CompletableFuture<String> fileFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return fetchFile();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture.allOf(blockFuture, fileFuture).join();

        long duration = System.currentTimeMillis() - startTime;
        String result = String.format("CompletableFuture Combined: %s | %s (Total: %dms)", 
            blockFuture.get(), fileFuture.get(), duration);
        
        return result;
    }

    private static String firstSuccessWithStructuredConcurrency() throws Exception {
        long startTime = System.currentTimeMillis();

        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<String>allUntil(s -> s.state() == Subtask.State.SUCCESS)
        )) {
            scope.fork(() -> slowService("Cache-1", 500));
            scope.fork(() -> slowService("Cache-2", 200));
            scope.fork(() -> slowService("Database", 800));

            Stream<Subtask<String>> results = scope.join();

            long duration = System.currentTimeMillis() - startTime;
            String firstResult = results
                .filter(s -> s.state() == Subtask.State.SUCCESS)
                .findFirst()
                .map(Subtask::get)
                .orElseThrow(() -> new Exception("No successful result"));

            String result = String.format("First successful result: %s (Duration: %dms)",
                firstResult, duration);

            return result;
        }
    }

    private static String aggregateWithFallback() {
        long startTime = System.currentTimeMillis();
        
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            var blockFuture = scope.fork(() -> fetchBlock());
            var fileFuture = scope.fork(() -> fetchFileWithPossibleError());

            scope.join();

            long duration = System.currentTimeMillis() - startTime;
            String result = String.format("Aggregate with fallback: %s | %s (Duration: %dms)", 
                blockFuture.get(), fileFuture.get(), duration);
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            return String.format("Fallback response: One service failed (%s), but we handled it gracefully (Duration: %dms)", 
                e.getMessage(), duration);
        }
    }

    private static String multiServiceAggregation() throws Exception {
        long startTime = System.currentTimeMillis();
        
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            var blockFuture = scope.fork(() -> fetchBlock());
            var fileFuture = scope.fork(() -> fetchFile());
            var computeFuture = scope.fork(() -> fetchCompute());
            var cacheFuture = scope.fork(() -> slowService("Cache", 150));

            scope.join();

            long duration = System.currentTimeMillis() - startTime;
            String result = String.format("Multi-service result: Block[%s] | File[%s] | Compute[%s] | Cache[%s] (Total: %dms)", 
                blockFuture.get(), fileFuture.get(), computeFuture.get(), cacheFuture.get(), duration);
            
            return result;
        }
    }

    private static String fetchBlock() throws Exception {
        Thread.sleep(300);
        return "Block-Service-OK";
    }

    private static String fetchFile() throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(LARGE_FILE));
        return "File-Service-OK-" + lines.size() + "-lines";
    }

    private static String fetchCompute() {
        long result = 0;
        for (int i = 2; i <= 10_000; i++) {
            if (isPrime(i)) {
                result += i;
            }
        }
        return "Compute-Service-OK-" + result;
    }

    private static String fetchFileWithPossibleError() throws Exception {
        if (Math.random() < 0.3) {
            throw new RuntimeException("File service temporarily unavailable");
        }
        return fetchFile();
    }

    private static String slowService(String serviceName, long delay) throws Exception {
        Thread.sleep(delay);
        return serviceName + "-OK-" + delay + "ms";
    }

    private static String fetchExternalBlock() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/block"))
            .GET()
            .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private static String fetchExternalFile() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/file"))
            .GET()
            .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
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
        try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)) {
            scheduler.scheduleAtFixedRate(() -> {
                updateCpuUsage();
                System.out.printf("[METRICS] Active: %d, Total: %d, Avg Response: %.2fms, CPU: %.2f%%, Memory: %.2fMB%n",
                        activeRequests.get(),
                        totalRequests.get(),
                        totalRequests.get() > 0 ? (double) totalResponseTime.get() / totalRequests.get() : 0,
                        cpuUsage,
                        (runtime.totalMemory() - runtime.freeMemory()) / 1024.0 / 1024.0
                );
            }, 5, 5, TimeUnit.SECONDS);
        }
    }

    private static void updateCpuUsage() {
        try {
            OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            cpuUsage = osBean.getCpuLoad() * 100;
        } catch (Exception e) {
            cpuUsage = 0.0;
        }
    }

    private static String generateMetrics() {
        updateCpuUsage();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();

        return String.format("""
            Virtual Thread Microservice Metrics:
            =====================================
            Active Requests: %d
            Total Requests: %d
            Average Response Time: %.2fms
            CPU Usage: %.2f%%
            Memory Usage: %.2fMB / %.2fMB
            JVM Uptime: %d seconds
            Thread Type: Virtual Threads
            """,
                activeRequests.get(),
                totalRequests.get(),
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
            logger.error("Failed to clean up test file: {}", e.getMessage());
        }
    }

    private static void sendResponse(HttpExchange exchange, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, response.length());
        exchange.getResponseBody().write(response.getBytes());
        exchange.close();
    }

    private static void sendErrorResponse(HttpExchange exchange, String error) {
        try {
            exchange.sendResponseHeaders(500, error.length());
            exchange.getResponseBody().write(error.getBytes());
            exchange.close();
        } catch (IOException e) {
            logger.error("Failed to send error response: {}", e.getMessage());
        }
    }
}
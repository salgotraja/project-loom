package app.js.threads;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class ThreadOptimizedMicroservice {
    private static final Logger logger = LoggerFactory.getLogger(ThreadOptimizedMicroservice.class);
    public static final int PORT = 8086;
    private static final String LARGE_FILE = "thread_test_file.txt";

    private static final ExecutorService virtualThreadExecutor = 
        Executors.newVirtualThreadPerTaskExecutor();

    private static final ExecutorService cpuIntensiveExecutor = 
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private static final ExecutorService ioExecutor = 
        Executors.newVirtualThreadPerTaskExecutor();

    private static final AtomicLong virtualThreadCount = new AtomicLong(0);
    private static final AtomicLong platformThreadCount = new AtomicLong(0);
    private static final LongAdder totalRequests = new LongAdder();
    private static final LongAdder totalResponseTime = new LongAdder();

    static void main(String[] args) throws Exception {
        createTestFile();
        startThreadMonitoring();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(virtualThreadExecutor);

        server.createContext("/compute-optimized", exchange -> handleRequest(exchange, "COMPUTE_OPTIMIZED", () -> {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                platformThreadCount.incrementAndGet();
                try {
                    long result = 0;
                    for (int i = 2; i <= 100_000; i++) {
                        if (isPrime(i)) {
                            result += i;
                        }
                    }
                    return "CPU Task completed on platform thread. Result: " + result;
                } finally {
                    platformThreadCount.decrementAndGet();
                }
            }, cpuIntensiveExecutor);

            return future.get();
        }));

        server.createContext("/io-optimized", exchange -> handleRequest(exchange, "IO_OPTIMIZED", () -> {
            virtualThreadCount.incrementAndGet();
            try {
                Thread.sleep(300);
                List<String> lines = Files.readAllLines(Paths.get(LARGE_FILE));
                return "I/O Task completed on virtual thread. Lines: " + lines.size();
            } finally {
                virtualThreadCount.decrementAndGet();
            }
        }));

        server.createContext("/mixed-workload", exchange -> handleRequest(exchange, "MIXED_WORKLOAD", () -> {
            CompletableFuture<String> cpuTask = CompletableFuture.supplyAsync(() -> {
                platformThreadCount.incrementAndGet();
                try {
                    long result = 0;
                    for (int i = 2; i <= 50_000; i++) {
                        if (isPrime(i)) result += i;
                    }
                    return "CPU: " + result;
                } finally {
                    platformThreadCount.decrementAndGet();
                }
            }, cpuIntensiveExecutor);

            CompletableFuture<String> ioTask = CompletableFuture.supplyAsync(() -> {
                virtualThreadCount.incrementAndGet();
                try {
                    Thread.sleep(200);
                    return "I/O: completed";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "I/O: interrupted";
                } finally {
                    virtualThreadCount.decrementAndGet();
                }
            }, ioExecutor);

            String cpuResult = cpuTask.get();
            String ioResult = ioTask.get();
            return "Mixed workload: " + cpuResult + " | " + ioResult;
        }));

        server.createContext("/thread-stats", exchange -> {
            String stats = generateThreadStats();
            sendResponse(exchange, stats);
        });

        server.start();
        logger.info(" Thread-Optimized Microservice started on port " + PORT);
        logger.info("Endpoints:");
        logger.info("  GET /compute-optimized - CPU-intensive on platform threads");
        logger.info("  GET /io-optimized      - I/O operations on virtual threads");
        logger.info("  GET /mixed-workload    - Mixed CPU/I/O workload");
        logger.info("  GET /thread-stats      - Thread statistics");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("\nShutting down Thread-Optimized Microservice...");
            server.stop(2);
            cpuIntensiveExecutor.shutdown();
            ioExecutor.shutdown();
            virtualThreadExecutor.shutdown();
            cleanupTestFile();
        }));
    }

    private static void startThreadMonitoring() {
        try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)) {
            scheduler.scheduleAtFixedRate(() -> System.out.printf("[THREADS] Virtual: %d, Platform: %d, Total Requests: %d%n",
                    virtualThreadCount.get(),
                    platformThreadCount.get(),
                    totalRequests.longValue()), 0, 5, TimeUnit.SECONDS);
        }
    }

    private static String generateThreadStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("Thread Statistics:\n");
        stats.append("==================\n");
        stats.append("Active Virtual Threads: ").append(virtualThreadCount.get()).append("\n");
        stats.append("Active Platform Threads: ").append(platformThreadCount.get()).append("\n");
        stats.append("Total Requests: ").append(totalRequests.longValue()).append("\n");
        stats.append("Available Processors: ").append(Runtime.getRuntime().availableProcessors()).append("\n");
        
        long totalReq = totalRequests.longValue();
        if (totalReq > 0) {
            stats.append("Average Response Time: ").append(totalResponseTime.longValue() / totalReq).append("ms\n");
        }

        return stats.toString();
    }

    private static void handleRequest(HttpExchange exchange, String endpoint, RequestHandler handler) {
        long startTime = System.currentTimeMillis();
        totalRequests.increment();

        try {
            String result = handler.handle();
            long duration = System.currentTimeMillis() - startTime;
            totalResponseTime.add(duration);

            String response = String.format("%s (Duration: %dms, Thread: %s)",
                    result, duration, Thread.currentThread().getName());

            sendResponse(exchange, response);
        } catch (Exception e) {
            sendErrorResponse(exchange, "Error: " + e.getMessage());
        }
    }

    @FunctionalInterface
    interface RequestHandler {
        String handle() throws Exception;
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
        if (!Files.exists(Paths.get(LARGE_FILE))) {
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < 10_000; i++) {
                content.append("Thread test line ").append(i + 1).append("\n");
            }
            Files.writeString(Paths.get(LARGE_FILE), content.toString());
        }
    }

    private static void cleanupTestFile() {
        try {
            Files.deleteIfExists(Paths.get(LARGE_FILE));
        } catch (IOException e) {
            System.err.println("Failed to clean up test file: " + e.getMessage());
        }
    }

    private static void sendResponse(HttpExchange exchange, String response) {
        try {
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();
        } catch (IOException e) {
            logger.error("Failed to send response: {}", e.getMessage());
        }
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
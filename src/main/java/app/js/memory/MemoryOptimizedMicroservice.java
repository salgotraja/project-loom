package app.js.memory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.ArrayList;

public class MemoryOptimizedMicroservice {
    private static final Logger logger = LoggerFactory.getLogger(MemoryOptimizedMicroservice.class);
    public static final int PORT = 8084;
    private static final String LARGE_FILE = "memory_test_file.txt";

    private static final Map<String, LongAdder> endpointMemoryUsage = new ConcurrentHashMap<>();

    private static final AtomicLong gcCount = new AtomicLong(0);
    private static final AtomicLong lastGcTime = new AtomicLong(0);

    private static final LongAdder requestCounter = new LongAdder();
    private static final LongAdder totalRequests = new LongAdder();
    private static final LongAdder totalResponseTime = new LongAdder();
    private static final LongAdder activeRequests = new LongAdder();

    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private static volatile long lastHeapUsage = 0;
    private static volatile long heapGrowthRate = 0;

    private static ScheduledExecutorService memoryMonitorScheduler;
    private static ScheduledExecutorService gcMonitorScheduler;

    static void main(String[] args) throws Exception {
        createTestFile();
        startMemoryMonitoring();
        startGCMonitoring();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/memory-leak", exchange -> {
            handleOptimizedRequest(exchange, "MEMORY_LEAK", () -> {
                List<byte[]> memoryHog = new ArrayList<>();
                for (int i = 0; i < 1000; i++) {
                    memoryHog.add(new byte[1024 * 1024]);
                }
                return "Memory leak simulation completed. Allocated: " + memoryHog.size() + "MB";
            });
        });

        server.createContext("/cpu-intensive", exchange -> {
            handleOptimizedRequest(exchange, "CPU_INTENSIVE", () -> {
                long result = 0;
                for (int i = 2; i <= 100_000; i++) {
                    if (isPrime(i)) {
                        result += i;
                    }
                }
                return "CPU Task completed. Result: " + result;
            });
        });

        server.createContext("/file-io", exchange -> {
            handleOptimizedRequest(exchange, "FILE_IO", () -> {
                try {
                    List<String> lines = Files.readAllLines(Paths.get(LARGE_FILE));
                    return "File read completed. Lines: " + lines.size();
                } catch (IOException e) {
                    throw new RuntimeException("File read error", e);
                }
            });
        });

        server.createContext("/memory-stats", exchange -> {
            String stats = generateMemoryStats();
            sendResponse(exchange, stats);
        });

        server.createContext("/gc", exchange -> {
            System.gc();
            sendResponse(exchange, "Garbage collection triggered");
        });

        server.createContext("/health", exchange -> {
            sendResponse(exchange, "Memory-Optimized Microservice is running!");
        });

        server.start();
        logger.info(" Memory-Optimized Virtual Thread Microservice started on port " + PORT);
        logger.info("Endpoints:");
        logger.info("  GET /memory-leak       - Simulate memory leak");
        logger.info("  GET /cpu-intensive     - CPU-intensive task");
        logger.info("  GET /file-io           - File I/O operations");
        logger.info("  GET /memory-stats      - Memory statistics");
        logger.info("  GET /gc                - Force garbage collection");
        logger.info("  GET /health            - Health check");
        logger.info("\nMemory monitoring enabled - check /memory-stats endpoint");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("\nShutting down Memory-Optimized Microservice...");
            server.stop(2);
            if (memoryMonitorScheduler != null) memoryMonitorScheduler.shutdown();
            if (gcMonitorScheduler != null) gcMonitorScheduler.shutdown();
            cleanupTestFile();
        }));
    }

    private static void startMemoryMonitoring() {
        memoryMonitorScheduler = Executors.newScheduledThreadPool(1);
        memoryMonitorScheduler.scheduleAtFixedRate(() -> {
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

            long currentHeapUsage = heapUsage.getUsed();
            heapGrowthRate = currentHeapUsage - lastHeapUsage;
            lastHeapUsage = currentHeapUsage;

            logger.info("Heap usage: {}MB", currentHeapUsage / 1024 / 1024);

            if (heapGrowthRate > 50 * 1024 * 1024) {
                logger.error("  WARNING: High memory growth detected: {}MB", heapGrowthRate / 1024 / 1024);
            }

            double heapUsagePercent = (double) currentHeapUsage / heapUsage.getMax() * 100;
            if (heapUsagePercent > 80) {
                logger.error("\uD83D\uDD25 CRITICAL: Heap usage at {}", String.format("%.2f%%", heapUsagePercent));
                System.gc();
            }

            System.out.printf("[MEMORY] Heap: %.2fMB/%.2fMB (%.2f%%), NonHeap: %.2fMB, Growth: %+.2fMB%n",
                    currentHeapUsage / 1024.0 / 1024.0,
                    heapUsage.getMax() / 1024.0 / 1024.0,
                    heapUsagePercent,
                    nonHeapUsage.getUsed() / 1024.0 / 1024.0,
                    heapGrowthRate / 1024.0 / 1024.0
            );
        }, 0, 10, TimeUnit.SECONDS);
    }

    private static void startGCMonitoring() {
        gcMonitorScheduler = Executors.newScheduledThreadPool(1);
        gcMonitorScheduler.scheduleAtFixedRate(() -> {
            long currentGcCount = ManagementFactory.getGarbageCollectorMXBeans()
                    .stream()
                    .mapToLong(GarbageCollectorMXBean::getCollectionCount)
                    .sum();

            long currentGcTime = ManagementFactory.getGarbageCollectorMXBeans()
                    .stream()
                    .mapToLong(GarbageCollectorMXBean::getCollectionTime)
                    .sum();

            if (currentGcCount > gcCount.get()) {
                long gcDelta = currentGcTime - lastGcTime.get();
                System.out.printf("[GC] Collections: %d, Time: %dms%n", 
                    currentGcCount - gcCount.get(), gcDelta);

                if (gcDelta > 1000) {
                    logger.error("  WARNING: High GC time detected: {}ms", gcDelta);
                }
            }

            gcCount.set(currentGcCount);
            lastGcTime.set(currentGcTime);
        }, 0, 5, TimeUnit.SECONDS);
    }

    private static void handleOptimizedRequest(HttpExchange exchange, String endpoint, RequestHandler handler) {
        long startTime = System.currentTimeMillis();
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        activeRequests.increment();
        requestCounter.increment();

        try {
            String result = handler.handle();
            long duration = System.currentTimeMillis() - startTime;
            long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long memoryDelta = endMemory - startMemory;
            logger.info("Request: {} (Duration: {}ms, Memory: {}MB)", endpoint, duration, memoryDelta / 1024 / 1024);

            endpointMemoryUsage.computeIfAbsent(endpoint, k -> new LongAdder()).add(memoryDelta);

            totalRequests.increment();
            totalResponseTime.add(duration);

            if (memoryDelta > 10 * 1024 * 1024) {
                System.err.println("  HIGH MEMORY REQUEST: " + endpoint + " used " +
                        (memoryDelta / 1024 / 1024) + "MB");
            }

            String response = String.format("%s (Duration: %dms, Memory: %+.2fMB, Thread: %s)",
                    result, duration, memoryDelta / 1024.0 / 1024.0, Thread.currentThread().getName());

            sendResponse(exchange, response);

        } catch (Exception e) {
            sendErrorResponse(exchange, "Error: " + e.getMessage());
        } finally {
            activeRequests.decrement();
        }
    }

    private static String generateMemoryStats() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

        StringBuilder stats = new StringBuilder();
        stats.append("Memory Statistics:\n");
        stats.append("==================\n");
        stats.append(String.format("Heap Usage: %.2fMB / %.2fMB (%.2f%%)\n",
                heapUsage.getUsed() / 1024.0 / 1024.0,
                heapUsage.getMax() / 1024.0 / 1024.0,
                (double) heapUsage.getUsed() / heapUsage.getMax() * 100));

        stats.append(String.format("Non-Heap Usage: %.2fMB / %.2fMB\n",
                nonHeapUsage.getUsed() / 1024.0 / 1024.0,
                nonHeapUsage.getMax() / 1024.0 / 1024.0));

        stats.append(String.format("Memory Growth Rate: %+.2fMB/10s\n",
                heapGrowthRate / 1024.0 / 1024.0));

        stats.append("\nEndpoint Memory Usage:\n");
        endpointMemoryUsage.forEach((endpoint, usage) -> stats.append(String.format("  %s: %.2fMB total\n",
                endpoint, usage.sum() / 1024.0 / 1024.0)));

        stats.append("\nRequest Statistics:\n");
        stats.append("Active Requests: ").append(activeRequests.longValue()).append("\n");
        stats.append("Total Requests: ").append(totalRequests.longValue()).append("\n");
        long totalReq = totalRequests.longValue();
        if (totalReq > 0) {
            stats.append("Average Response Time: ").append(totalResponseTime.longValue() / totalReq).append("ms\n");
        }

        return stats.toString();
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
        Path filePath = Paths.get(LARGE_FILE);
        if (!Files.exists(filePath)) {
            logger.info("Creating test file: {}", LARGE_FILE);
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < 50_000; i++) {
                content.append("This is line ").append(i + 1)
                        .append(" of the memory test file with data for benchmarking.\n");
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
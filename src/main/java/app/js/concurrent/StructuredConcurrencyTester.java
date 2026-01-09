package app.js.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class StructuredConcurrencyTester {
    private static final Logger logger = LoggerFactory.getLogger(StructuredConcurrencyComparison.class);
    
    private static final int WARMUP_ITERATIONS = 100;
    private static final int TEST_ITERATIONS = 1000;
    private static final AtomicInteger testCounter = new AtomicInteger(0);
    
    static void main(String[] args) throws Exception {
        logger.info(" Comprehensive Structured Concurrency Testing");
        logger.info("=" .repeat(80));

        testTimingAccuracy();
        testMemoryUsage();
        testThreadBehavior();
        testErrorHandling();
        testPerformanceUnderLoad();

        testCancellationBehavior();
        
        logger.info("\n All tests completed!");
    }
    
    private static void testTimingAccuracy() throws Exception {
        logger.info("\n Test 1: Timing Accuracy");
        logger.info("-".repeat(50));
        
        long expectedTime = 300;

        long structuredStart = System.nanoTime();
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var t1 = scope.fork(() -> timedTask("Task-1", 100));
            var t2 = scope.fork(() -> timedTask("Task-2", 200));
            var t3 = scope.fork(() -> timedTask("Task-3", 300));
            
            scope.join();
            scope.throwIfFailed();

            logger.info("Results: {}, {}, {}", t1.get(), t2.get(), t3.get());
        }
        long structuredTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - structuredStart);

        long cfStart = System.nanoTime();
        CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(() -> timedTask("Task-1", 100));
        CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> timedTask("Task-2", 200));
        CompletableFuture<String> cf3 = CompletableFuture.supplyAsync(() -> timedTask("Task-3", 300));
        
        CompletableFuture.allOf(cf1, cf2, cf3).join();
        logger.info("Results: {}, {}, {}", cf1.get(), cf2.get(), cf3.get());
        long cfTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - cfStart);

        logger.info("\n Timing Analysis:");
        logger.info("Expected time: ~{}ms", expectedTime);
        logger.info("StructuredTaskScope: {}ms (overhead: {}ms)", structuredTime, structuredTime - expectedTime);
        logger.info("CompletableFuture: {}ms (overhead: {}ms)", cfTime, cfTime - expectedTime);
        
        boolean structuredAccurate = Math.abs(structuredTime - expectedTime) < 50;
        boolean cfAccurate = Math.abs(cfTime - expectedTime) < 50;

        logger.info(" StructuredTaskScope timing: {}", structuredAccurate ? "ACCURATE" : "INACCURATE");
        logger.info(" CompletableFuture timing: {}", cfAccurate ? "ACCURATE" : "INACCURATE");
    }
    
    private static void testMemoryUsage() throws Exception {
        logger.info("\n Test 2: Memory Usage");
        logger.info("-".repeat(50));
        
        Runtime runtime = Runtime.getRuntime();

        System.gc();
        Thread.sleep(100);
        long beforeStructured = runtime.totalMemory() - runtime.freeMemory();
        
        for (int i = 0; i < 100; i++) {
            final int taskId = i;
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                var t1 = scope.fork(() -> timedTask("Task-" + taskId, 1));
                var t2 = scope.fork(() -> timedTask("Task-" + taskId, 1));
                scope.join();
                scope.throwIfFailed();
                t1.get();
                t2.get();
            }
        }
        
        System.gc();
        Thread.sleep(100);
        long afterStructured = runtime.totalMemory() - runtime.freeMemory();

        System.gc();
        Thread.sleep(100);
        long beforeCF = runtime.totalMemory() - runtime.freeMemory();
        
        for (int i = 0; i < 100; i++) {
            final int taskId = i;
            CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(() -> timedTask("Task-" + taskId, 1));
            CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> timedTask("Task-" + taskId, 1));
            CompletableFuture.allOf(cf1, cf2).join();
            cf1.get();
            cf2.get();
        }
        
        System.gc();
        Thread.sleep(100);
        long afterCF = runtime.totalMemory() - runtime.freeMemory();

        long structuredMemory = afterStructured - beforeStructured;
        long cfMemory = afterCF - beforeCF;
        
        logger.info(" Memory Usage (100 iterations):");
        logger.info("StructuredTaskScope: " + (structuredMemory / 1024) + " KB");
        logger.info("CompletableFuture: " + (cfMemory / 1024) + " KB");
        logger.info("Difference: " + ((cfMemory - structuredMemory) / 1024) + " KB");
    }
    
    private static void testThreadBehavior() throws Exception {
        logger.info("\n Test 3: Thread Behavior");
        logger.info("-".repeat(50));
        
        AtomicInteger structuredThreadCount = new AtomicInteger(0);
        AtomicInteger cfThreadCount = new AtomicInteger(0);

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            for (int i = 0; i < 10; i++) {
                scope.fork(() -> {
                    String threadName = Thread.currentThread().getName();
                    structuredThreadCount.incrementAndGet();
                    logger.info("StructuredTaskScope thread: " + threadName);
                    return timedTask("ST-Task", 10);
                });
            }
            scope.join();
            scope.throwIfFailed();
        }

        CompletableFuture<?>[] futures = new CompletableFuture[10];
        for (int i = 0; i < 10; i++) {
            futures[i] = CompletableFuture.supplyAsync(() -> {
                String threadName = Thread.currentThread().getName();
                cfThreadCount.incrementAndGet();
                logger.info("CompletableFuture thread: " + threadName);
                return timedTask("CF-Task", 10);
            });
        }
        CompletableFuture.allOf(futures).join();
        
        logger.info("\n Thread Analysis:");
        logger.info("StructuredTaskScope threads used: {}", structuredThreadCount.get());
        logger.info("CompletableFuture threads used: {}", cfThreadCount.get());
    }
    
    private static void testErrorHandling() throws Exception {
        logger.info("\n Test 4: Error Handling");
        logger.info("-".repeat(50));

        logger.info("Testing StructuredTaskScope error handling...");
        long structuredStart = System.nanoTime();
        boolean structuredCaughtError = false;
        
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            scope.fork(() -> timedTask("Good-Task-1", 100));
            scope.fork(() -> failingTask("Bad-Task", 50));
            scope.fork(() -> timedTask("Good-Task-2", 200));
            
            scope.join();
            scope.throwIfFailed();
        } catch (Exception e) {
            structuredCaughtError = true;
            long structuredErrorTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - structuredStart);
            logger.error("StructuredTaskScope caught error in: {}ms", structuredErrorTime);
            logger.error("Error message: {}", e.getMessage());
        }

        logger.info("\nTesting CompletableFuture error handling...");
        long cfStart = System.nanoTime();
        boolean cfCaughtError = false;
        
        try {
            CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(() -> timedTask("Good-Task-1", 100));
            CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> failingTask("Bad-Task", 50));
            CompletableFuture<String> cf3 = CompletableFuture.supplyAsync(() -> timedTask("Good-Task-2", 200));
            
            CompletableFuture.allOf(cf1, cf2, cf3).join();
        } catch (Exception e) {
            cfCaughtError = true;
            long cfErrorTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - cfStart);
            logger.error("CompletableFuture caught error in: {}ms", cfErrorTime);
            logger.error("Error: {}", e.getCause().getMessage());
        }
        
        logger.info("\n Error Handling Analysis:");
        logger.info("StructuredTaskScope error caught: {}", structuredCaughtError);
        logger.info("CompletableFuture error caught: {}", cfCaughtError);
    }
    
    private static void testPerformanceUnderLoad() {
        logger.info("\n Test 5: Performance Under Load");
        logger.info("-".repeat(50));

        logger.info("Warming up...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runStructuredTaskScopeTest();
            runCompletableFutureTest();
        }

        logger.info("Running performance test (" + TEST_ITERATIONS + " iterations)...");
        
        long structuredTotal = 0;
        long cfTotal = 0;
        
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            structuredTotal += runStructuredTaskScopeTest();
            cfTotal += runCompletableFutureTest();
            
            if (i % 100 == 0) {
                logger.info("Completed " + i + " iterations");
            }
        }
        
        double structuredAvg = (double) structuredTotal / TEST_ITERATIONS;
        double cfAvg = (double) cfTotal / TEST_ITERATIONS;
        
        logger.info("\n Performance Results (" + TEST_ITERATIONS + " iterations):");
        logger.info("StructuredTaskScope average: " + String.format("%.2f", structuredAvg) + " ms");
        logger.info("CompletableFuture average: " + String.format("%.2f", cfAvg) + " ms");
        logger.info("Difference: " + String.format("%.2f", cfAvg - structuredAvg) + " ms");
        logger.info("StructuredTaskScope is " + String.format("%.1f", (cfAvg / structuredAvg)) + "x faster");
    }
    
    private static void testCancellationBehavior() {
        logger.info("\n Test 6: Cancellation Behavior");
        logger.info("-".repeat(50));
        
        AtomicInteger structuredCancelled = new AtomicInteger(0);
        AtomicInteger cfCancelled = new AtomicInteger(0);

        logger.info("Testing StructuredTaskScope cancellation...");
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            scope.fork(() -> cancellableTask("Task-1", 1000, structuredCancelled));
            scope.fork(() -> cancellableTask("Task-2", 2000, structuredCancelled));
            scope.fork(() -> failingTask("Failing-Task", 100));
            
            scope.join();
            scope.throwIfFailed();
        } catch (Exception e) {
            logger.info("StructuredTaskScope cancelled " + structuredCancelled.get() + " tasks");
        }

        logger.info("\nTesting CompletableFuture cancellation...");
        try {
            CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(() -> cancellableTask("Task-1", 1000, cfCancelled));
            CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> cancellableTask("Task-2", 2000, cfCancelled));
            CompletableFuture<String> cf3 = CompletableFuture.supplyAsync(() -> failingTask("Failing-Task", 100));
            
            CompletableFuture.allOf(cf1, cf2, cf3).join();
        } catch (Exception e) {
            logger.error("CompletableFuture requires manual cancellation");
        }
        
        logger.info("\n Cancellation Analysis:");
        logger.info("StructuredTaskScope auto-cancelled: {} tasks", structuredCancelled.get());
        logger.info("CompletableFuture auto-cancelled: {} tasks", cfCancelled.get());
    }

    private static String timedTask(String name, long delayMs) {
        try {
            Thread.sleep(delayMs);
            return name + "-OK";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return name + "-INTERRUPTED";
        }
    }
    
    private static String failingTask(String name, long delayMs) {
        try {
            Thread.sleep(delayMs);
            throw new RuntimeException(name + " failed!");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(name + " interrupted!");
        }
    }
    
    private static String cancellableTask(String name, long delayMs, AtomicInteger cancelledCounter) {
        try {
            Thread.sleep(delayMs);
            return name + "-COMPLETED";
        } catch (InterruptedException e) {
            cancelledCounter.incrementAndGet();
            Thread.currentThread().interrupt();
            return name + "-CANCELLED";
        }
    }
    
    private static long runStructuredTaskScopeTest() {
        try {
            long start = System.nanoTime();
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                var t1 = scope.fork(() -> timedTask("A", 1));
                var t2 = scope.fork(() -> timedTask("B", 1));
                scope.join();
                scope.throwIfFailed();
                t1.get();
                t2.get();
            }
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        } catch (Exception e) {
            return -1;
        }
    }
    
    private static long runCompletableFutureTest() {
        try {
            long start = System.nanoTime();
            CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(() -> timedTask("A", 1));
            CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> timedTask("B", 1));
            CompletableFuture.allOf(cf1, cf2).join();
            cf1.get();
            cf2.get();
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        } catch (Exception e) {
            return -1;
        }
    }
}
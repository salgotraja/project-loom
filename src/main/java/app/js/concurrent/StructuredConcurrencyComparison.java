package app.js.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class StructuredConcurrencyComparison {
    private static final Logger logger = LoggerFactory.getLogger(StructuredConcurrencyComparison.class);

    static void main(String[] args) throws Exception {
        logger.info(" Structured Concurrency vs CompletableFuture Comparison");
        logger.info("=" .repeat(80));

        logger.info("\n1 Basic Parallel Execution");
        testBasicParallelExecution();

        logger.info("\n2 Error Handling");
        testErrorHandling();

        logger.info("\n3 First Successful Response");
        testFirstSuccessfulResponse();

        logger.info("\n4 Performance Comparison");
        performanceComparison();
    }
    
    private static void testBasicParallelExecution() throws Exception {
        logger.info("\n--- StructuredTaskScope ---");
        long start = System.currentTimeMillis();

        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            var task1 = scope.fork(() -> simulateService("Service-A", 200));
            var task2 = scope.fork(() -> simulateService("Service-B", 300));
            var task3 = scope.fork(() -> simulateService("Service-C", 100));

            scope.join();
            
            String result = String.format("Results: %s, %s, %s", 
                task1.get(), task2.get(), task3.get());
            logger.info(result);
        }
        
        long structuredTime = System.currentTimeMillis() - start;
        logger.info("StructuredTaskScope took: " + structuredTime + "ms");
        
        logger.info("\n--- CompletableFuture ---");
        start = System.currentTimeMillis();
        
        CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(() -> simulateService("Service-A", 200));
        CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> simulateService("Service-B", 300));
        CompletableFuture<String> cf3 = CompletableFuture.supplyAsync(() -> simulateService("Service-C", 100));
        
        CompletableFuture.allOf(cf1, cf2, cf3).join();
        
        String result = String.format("Results: %s, %s, %s", 
            cf1.get(), cf2.get(), cf3.get());
        logger.info(result);
        
        long completableFutureTime = System.currentTimeMillis() - start;
        logger.info("CompletableFuture took: " + completableFutureTime + "ms");
    }
    
    private static void testErrorHandling() {
        logger.info("\n--- StructuredTaskScope Error Handling ---");

        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            var task1 = scope.fork(() -> simulateService("Service-A", 200));
            var task2 = scope.fork(() -> simulateFailingService("Service-B", 300));
            var task3 = scope.fork(() -> simulateService("Service-C", 100));

            scope.join();

            logger.info("This won't be reached due to failure");

        } catch (Exception e) {
            logger.error("Caught exception with message: {}", e.getMessage());
            logger.error("All tasks were automatically cancelled");
        }
        
        logger.info("\n--- CompletableFuture Error Handling ---");
        
        CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(() -> simulateService("Service-A", 200));
        CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> simulateFailingService("Service-B", 300));
        CompletableFuture<String> cf3 = CompletableFuture.supplyAsync(() -> simulateService("Service-C", 100));
        
        try {
            CompletableFuture.allOf(cf1, cf2, cf3).join();
        } catch (Exception e) {
            logger.error("Caught exception: {}", e.getCause().getMessage());
            logger.error("Manual cancellation needed for running tasks");
            cf1.cancel(true);
            cf3.cancel(true);
        }
    }
    
    private static void testFirstSuccessfulResponse() throws Exception {
        logger.info("\n--- StructuredTaskScope ShutdownOnSuccess ---");
        long start = System.currentTimeMillis();

        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<String>allUntil(s -> s.state() == Subtask.State.SUCCESS)
        )) {
            scope.fork(() -> simulateService("Slow-Service", 1000));
            scope.fork(() -> simulateService("Fast-Service", 200));
            scope.fork(() -> simulateService("Medium-Service", 500));

            Stream<Subtask<String>> results = scope.join();

            String result = results
                .filter(s -> s.state() == Subtask.State.SUCCESS)
                .findFirst()
                .map(Subtask::get)
                .orElseThrow(() -> new Exception("No successful result"));
            long duration = System.currentTimeMillis() - start;
            logger.info("First result: {} (took {}ms)", result, duration);
        }
        
        logger.info("\n--- CompletableFuture First Success (more complex) ---");
        start = System.currentTimeMillis();
        
        CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(() -> simulateService("Slow-Service", 1000));
        CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> simulateService("Fast-Service", 200));
        CompletableFuture<String> cf3 = CompletableFuture.supplyAsync(() -> simulateService("Medium-Service", 500));
        
        CompletableFuture<Object> firstCompleted = CompletableFuture.anyOf(cf1, cf2, cf3);
        String result = (String) firstCompleted.join();
        long duration = System.currentTimeMillis() - start;

        logger.info("First result: {} (took {}ms)", result, duration);

        cf1.cancel(true);
        cf2.cancel(true);
        cf3.cancel(true);
    }
    
    private static void performanceComparison() throws Exception {
        logger.info("\n--- Performance Comparison (1000 iterations) ---");

        for (int i = 0; i < 100; i++) {
            runStructuredTaskScope();
            runCompletableFuture();
        }

        long structuredStart = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            runStructuredTaskScope();
        }
        long structuredTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - structuredStart);

        long completableFutureStart = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            runCompletableFuture();
        }
        long completableFutureTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - completableFutureStart);

        logger.info("StructuredTaskScope: {}ms", structuredTime);
        logger.info("CompletableFuture: {}ms", completableFutureTime);
        logger.info("Difference: {}ms", completableFutureTime - structuredTime);
    }
    
    private static void runStructuredTaskScope() throws Exception {
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            var task1 = scope.fork(() -> simulateService("A", 1));
            var task2 = scope.fork(() -> simulateService("B", 1));

            scope.join();

            task1.get();
            task2.get();
        }
    }
    
    private static void runCompletableFuture() throws Exception {
        CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(() -> simulateService("A", 1));
        CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> simulateService("B", 1));
        
        CompletableFuture.allOf(cf1, cf2).join();
        
        cf1.get();
        cf2.get();
    }
    
    private static String simulateService(String name, long delayMs) {
        try {
            Thread.sleep(delayMs);
            return name + "-OK";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted: " + name, e);
        }
    }
    
    private static String simulateFailingService(String name, long delayMs) {
        try {
            Thread.sleep(delayMs);
            throw new RuntimeException(name + " failed!");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted: " + name, e);
        }
    }
}
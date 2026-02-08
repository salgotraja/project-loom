package app.js.microservices;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class ConcurrentServiceLayer {
    private static final Logger logger = LoggerFactory.getLogger(ConcurrentServiceLayer.class);

    private final AtomicInteger circuitBreakerFailures = new AtomicInteger(0);
    private final AtomicInteger retryAttempts = new AtomicInteger(0);

    public String structuredDbCall() throws Exception {
        logger.info("Starting structured DB call");
        
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            var userQuery = scope.fork(() -> simulateDbQuery("users", 150));
            var orderQuery = scope.fork(() -> simulateDbQuery("orders", 120));
            var productQuery = scope.fork(() -> simulateDbQuery("products", 180));
            
            scope.join();
            
            String result = String.format("DB Results: %s, %s, %s", 
                userQuery.get(), orderQuery.get(), productQuery.get());
            
            logger.info("Structured DB call completed successfully");
            return result;
        }
    }
    
    public String structuredFileOperation() throws Exception {
        logger.info("Starting structured file operation");
        
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            var readFile = scope.fork(() -> readTestFile());
            var writeFile = scope.fork(() -> writeLogFile());
            var validateFile = scope.fork(() -> validateFileIntegrity());
            
            scope.join();
            
            String result = String.format("File Operations: %s, %s, %s", 
                readFile.get(), writeFile.get(), validateFile.get());
            
            logger.info("Structured file operation completed successfully");
            return result;
        }
    }
    
    public String structuredAggregateServices() throws Exception {
        logger.info("Starting structured service aggregation");
        
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            var authService = scope.fork(() -> simulateServiceCall("auth-service", 100));
            var userService = scope.fork(() -> simulateServiceCall("user-service", 150));
            var notificationService = scope.fork(() -> simulateServiceCall("notification-service", 80));
            var analyticsService = scope.fork(() -> simulateServiceCall("analytics-service", 200));
            
            scope.join();
            
            String result = String.format("Service Aggregation: %s, %s, %s, %s", 
                authService.get(), userService.get(), notificationService.get(), analyticsService.get());
            
            logger.info("Structured service aggregation completed successfully");
            return result;
        }
    }

    public String shortTimeoutExample() throws Exception {
        logger.info("Starting short timeout example");
        
        Instant deadline = Instant.now().plusMillis(300);
        
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            var slowTask = scope.fork(() -> simulateSlowService("slow-service", 500));
            var fastTask = scope.fork(() -> simulateSlowService("fast-service", 100));
            
            scope.join();
            
            if (Instant.now().isAfter(deadline)) {
                throw new TimeoutException("Request exceeded 300ms deadline");
            }
            
            
            String result = String.format("Timeout Results: %s, %s", 
                slowTask.get(), fastTask.get());
            
            logger.info("Short timeout example completed within deadline");
            return result;
        }
    }
    
    public String gracefulTimeoutExample() throws Exception {
        logger.info("Starting graceful timeout example");

        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<String>allUntil(s -> s.state() == Subtask.State.SUCCESS)
        )) {
            var primaryService = scope.fork(() -> simulateServiceCall("primary-service", 400));
            var fallbackService = scope.fork(() -> simulateServiceCall("fallback-service", 200));
            var cacheService = scope.fork(() -> simulateServiceCall("cache-service", 50));
            logger.info("primaryService: {}, fallbackService: {}, cacheService: {}", primaryService, fallbackService, cacheService);
            logger.info("Graceful timeout example starting");
            Stream<Subtask<String>> results = scope.join();

            String firstResult = results
                .filter(s -> s.state() == Subtask.State.SUCCESS)
                .findFirst()
                .map(Subtask::get)
                .orElseThrow(() -> new Exception("No successful result"));

            String result = "Graceful Timeout Result: " + firstResult;
            logger.info("Graceful timeout example completed with first success");
            return result;
        }
    }
    
    public String strictDeadlineExample() throws Exception {
        logger.info("Starting strict deadline example");
        
        Instant deadline = Instant.now().plusMillis(800);
        
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            var task1 = scope.fork(() -> simulateTimedTask("task-1", 200, deadline));
            var task2 = scope.fork(() -> simulateTimedTask("task-2", 300, deadline));
            var task3 = scope.fork(() -> simulateTimedTask("task-3", 400, deadline));
            
            scope.join();
            
            String result = String.format("Deadline Results: %s, %s, %s", 
                task1.get(), task2.get(), task3.get());
            
            logger.info("Strict deadline example completed within deadline");
            return result;
        }
    }

    public String asyncMultiServiceCall() throws Exception {
        logger.info("Starting async multi-service call");
        
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {

            var apiService1 = scope.fork(() -> asyncHttpCall("https://httpbin.org/delay/1", "api-1"));
            var apiService2 = scope.fork(() -> asyncHttpCall("https://httpbin.org/delay/2", "api-2"));
            var apiService3 = scope.fork(() -> asyncHttpCall("https://httpbin.org/json", "api-3"));
            
            scope.join();
            
            String result = String.format("Async HTTP Results: %s, %s, %s", 
                apiService1.get(), apiService2.get(), apiService3.get());
            
            logger.info("Async multi-service call completed successfully");
            return result;
        }
    }
    
    public String asyncFailoverCall() throws Exception {
        logger.info("Starting async failover call");

        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<String>allUntil(s -> s.state() == Subtask.State.SUCCESS)
        )) {
            var primaryApi = scope.fork(() -> asyncHttpCall("https://httpbin.org/status/500", "primary"));
            var secondaryApi = scope.fork(() -> asyncHttpCall("https://httpbin.org/json", "secondary"));
            var tertiaryApi = scope.fork(() -> asyncHttpCall("https://httpbin.org/uuid", "tertiary"));
            logger.info("primaryApi: {}, secondaryApi: {}, tertiaryApi: {}", primaryApi, secondaryApi, tertiaryApi);

            Stream<Subtask<String>> results = scope.join();

            String firstResult = results
                .filter(s -> s.state() == Subtask.State.SUCCESS)
                .findFirst()
                .map(Subtask::get)
                .orElseThrow(() -> new Exception("No successful result"));

            String result = "Failover Result: " + firstResult;
            logger.info("Async failover call completed with successful fallback");
            return result;
        }
    }
    
    public String asyncRaceCondition() throws Exception {
        logger.info("Starting async race condition handling");

        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<String>allUntil(s -> s.state() == Subtask.State.SUCCESS)
        )) {
            var cacheHit = scope.fork(() -> simulateServiceCall("cache-hit", 50));
            var dbQuery = scope.fork(() -> simulateServiceCall("db-query", 300));
            var apiCall = scope.fork(() -> simulateServiceCall("api-call", 200));
            logger.info("cacheHit: {}, dbQuery: {}, apiCall: {}", cacheHit, dbQuery, apiCall);
            Stream<Subtask<String>> results = scope.join();

            String firstResult = results
                .filter(s -> s.state() == Subtask.State.SUCCESS)
                .findFirst()
                .map(Subtask::get)
                .orElseThrow(() -> new Exception("No successful result"));

            String result = "Race Winner: " + firstResult;
            logger.info("Async race condition handled successfully");
            return result;
        }
    }

    public String servicePipeline() throws Exception {
        logger.info("Starting service pipeline");
        
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            var stage1 = scope.fork(() -> simulateServiceCall("validation-stage", 100));
            var stage2 = scope.fork(() -> simulateServiceCall("processing-stage", 150));
            var stage3 = scope.fork(() -> simulateServiceCall("enrichment-stage", 120));
            var stage4 = scope.fork(() -> simulateServiceCall("output-stage", 80));
            
            scope.join();
            
            String result = String.format("Pipeline Results: %s -> %s -> %s -> %s", 
                stage1.get(), stage2.get(), stage3.get(), stage4.get());
            
            logger.info("Service pipeline completed successfully");
            return result;
        }
    }
    
    public String circuitBreakerExample() throws Exception {
        logger.info("Starting circuit breaker example");
        
        if (circuitBreakerFailures.get() > 3) {
            logger.warn("Circuit breaker is OPEN - failing fast");
            return "Circuit breaker is OPEN - service unavailable";
        }
        
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            var primaryService = scope.fork(() -> {
                if (Math.random() < 0.3) {
                    circuitBreakerFailures.incrementAndGet();
                    throw new RuntimeException("Service failure");
                }
                circuitBreakerFailures.set(0);
                return simulateServiceCall("primary-service", 100);
            });
            
            scope.join();
            
            String result = "Circuit Breaker Result: " + primaryService.get();
            logger.info("Circuit breaker example completed successfully");
            return result;
        }
    }
    
    public String retryPatternExample() throws Exception {
        logger.info("Starting retry pattern example");
        
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            var retryableTask = scope.fork(() -> simulateRetryableService());
            
            scope.join();
            
            String result = "Retry Pattern Result: " + retryableTask.get();
            logger.info("Retry pattern example completed successfully");
            return result;
        }
    }

    public String scatterGatherPattern() throws Exception {
        logger.info("Starting scatter-gather pattern");
        
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            var service1 = scope.fork(() -> simulateServiceCall("service-1", 100));
            var service2 = scope.fork(() -> simulateServiceCall("service-2", 150));
            var service3 = scope.fork(() -> simulateServiceCall("service-3", 120));
            var service4 = scope.fork(() -> simulateServiceCall("service-4", 180));
            var service5 = scope.fork(() -> simulateServiceCall("service-5", 90));
            
            scope.join();

            String result = String.format("Scatter-Gather Results: [%s, %s, %s, %s, %s]", 
                service1.get(), service2.get(), service3.get(), service4.get(), service5.get());
            
            logger.info("Scatter-gather pattern completed successfully");
            return result;
        }
    }
    
    public String bulkheadPattern() throws Exception {
        logger.info("Starting bulkhead pattern");
        
        try (var criticalScope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow());
             var nonCriticalScope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {

            var criticalService1 = criticalScope.fork(() -> simulateServiceCall("critical-auth", 100));
            var criticalService2 = criticalScope.fork(() -> simulateServiceCall("critical-payment", 150));

            var nonCriticalService1 = nonCriticalScope.fork(() -> simulateServiceCall("analytics", 200));
            var nonCriticalService2 = nonCriticalScope.fork(() -> simulateServiceCall("logging", 50));
            logger.info("criticalService1: {}, criticalService2: {}, nonCriticalService1: {}, nonCriticalService2: {}", criticalService1, criticalService2, nonCriticalService1, nonCriticalService2);
            criticalScope.join();
            logger.info("Critical services completed successfully");
            try {
                nonCriticalScope.join();
            } catch (Exception e) {
                logger.warn("Non-critical services failed: {}", e.getMessage());
            }
            
            String result = String.format("Bulkhead Pattern: Critical[%s, %s] Non-Critical[%s, %s]", 
                criticalService1.get(), criticalService2.get(), 
                "analytics-ok", "logging-ok");
            
            logger.info("Bulkhead pattern completed successfully");
            return result;
        }
    }

    private String simulateDbQuery(String table, long delayMs) throws Exception {
        Thread.sleep(delayMs);
        return table + "-query-ok";
    }
    
    private String simulateServiceCall(String serviceName, long delayMs) throws Exception {
        Thread.sleep(delayMs);
        return serviceName + "-ok";
    }
    
    private String simulateSlowService(String serviceName, long delayMs) throws Exception {
        Thread.sleep(delayMs);
        return serviceName + "-completed";
    }
    
    private String simulateTimedTask(String taskName, long delayMs, Instant deadline) throws Exception {
        Thread.sleep(delayMs);
        
        if (Instant.now().isAfter(deadline)) {
            throw new TimeoutException(taskName + " exceeded deadline");
        }
        
        return taskName + "-within-deadline";
    }
    
    private String simulateRetryableService() throws Exception {
        int attempts = retryAttempts.incrementAndGet();
        
        if (attempts < 3) {
            Thread.sleep(100);
            throw new RuntimeException("Service temporarily unavailable (attempt " + attempts + ")");
        }
        
        retryAttempts.set(0);
        Thread.sleep(100);
        return "retryable-service-ok-after-" + attempts + "-attempts";
    }
    
    private String readTestFile() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("advanced_microservice_test_file.txt"));
        return "file-read-ok-" + lines.size() + "-lines";
    }
    
    private String writeLogFile() throws IOException {
        String logData = "Log entry: " + Instant.now() + " - " + MDC.get("traceId");
        Files.writeString(Paths.get("temp_log.txt"), logData);
        return "log-written-ok";
    }
    
    private String validateFileIntegrity() throws Exception {
        Thread.sleep(50);
        return "file-validation-ok";
    }
    
    private String asyncHttpCall(String url, String serviceName) {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return serviceName + "-http-ok-" + response.statusCode();
            } catch (Exception e) {
                return serviceName + "-http-fallback";
            }
        }
    }
}
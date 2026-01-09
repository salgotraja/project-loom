package app.js.structured;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;

public class ScopedRequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(ScopedRequestHandler.class);

    public <T> T runInScope(Callable<T> task) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var future = scope.fork(task);
            scope.join();
            scope.throwIfFailed();
            return future.get();
        }
    }

    public <T> T runInScopeWithTimeout(Callable<T> task, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var future = scope.fork(task);
            scope.join();
            
            if (Instant.now().isAfter(deadline)) {
                throw new TimeoutException("Operation exceeded timeout: " + timeout);
            }
            
            scope.throwIfFailed();
            return future.get();
        }
    }

    public <T1, T2> ParallelResult<T1, T2> runInParallel(
            Callable<T1> task1, 
            Callable<T2> task2) throws Exception {
        
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var future1 = scope.fork(task1);
            var future2 = scope.fork(task2);
            
            scope.join();
            scope.throwIfFailed();
            
            return new ParallelResult<>(future1.get(), future2.get());
        }
    }

    public <T1, T2, T3> TripleResult<T1, T2, T3> runInParallel(
            Callable<T1> task1, 
            Callable<T2> task2, 
            Callable<T3> task3) throws Exception {
        
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var future1 = scope.fork(task1);
            var future2 = scope.fork(task2);
            var future3 = scope.fork(task3);
            
            scope.join();
            scope.throwIfFailed();
            
            return new TripleResult<>(future1.get(), future2.get(), future3.get());
        }
    }

    public <T> T runFirstSuccess(Callable<T>... tasks) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnSuccess<T>()) {
            for (Callable<T> task : tasks) {
                scope.fork(task);
            }
            
            scope.join();
            return scope.result();
        }
    }

    public <T> T runWithFallback(Callable<T> primary, Callable<T> fallback) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var primaryFuture = scope.fork(primary);
            
            scope.join();
            
            try {
                scope.throwIfFailed();
                return primaryFuture.get();
            } catch (Exception e) {
                logger.warn("Primary task failed, using fallback: {}", e.getMessage());
                return fallback.call();
            }
        }
    }

    public <T> T runWithRetry(Callable<T> task, int maxRetries, Duration retryDelay) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return runInScope(task);
            } catch (Exception e) {
                lastException = e;
                logger.warn("Attempt {} failed: {}", attempt, e.getMessage());
                
                if (attempt < maxRetries) {
                    Thread.sleep(retryDelay.toMillis());
                }
            }
        }
        
        throw new RuntimeException("All " + maxRetries + " attempts failed", lastException);
    }

    public <T> T runWithCircuitBreaker(Callable<T> task, CircuitBreakerConfig config) throws Exception {
        if (config.isOpen()) {
            throw new RuntimeException("Circuit breaker is OPEN - failing fast");
        }
        
        try {
            T result = runInScope(task);
            config.onSuccess();
            return result;
        } catch (Exception e) {
            config.onFailure();
            throw e;
        }
    }

    public <T> AggregateResult<T> aggregate(AggregateRequest<T> request) throws Exception {
        long startTime = System.currentTimeMillis();
        
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var futures = request.getTasks().stream()
                .map(scope::fork)
                .toList();
            
            scope.join();
            scope.throwIfFailed();
            
            var results = futures.stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
            
            long duration = System.currentTimeMillis() - startTime;
            return new AggregateResult<>(results, duration);
        }
    }

    public record ParallelResult<T1, T2>(T1 result1, T2 result2) {}
    public record TripleResult<T1, T2, T3>(T1 result1, T2 result2, T3 result3) {}
    public record AggregateResult<T>(java.util.List<T> results, long durationMs) {}

    public static class AggregateRequest<T> {
        private final java.util.List<Callable<T>> tasks;
        
        public AggregateRequest(java.util.List<Callable<T>> tasks) {
            this.tasks = tasks;
        }
        
        public java.util.List<Callable<T>> getTasks() {
            return tasks;
        }
        
        public static <T> AggregateRequest<T> of(Callable<T>... tasks) {
            return new AggregateRequest<>(java.util.List.of(tasks));
        }
    }

    public static class CircuitBreakerConfig {
        private int failureCount = 0;
        private final int threshold;
        private final Duration timeout;
        private Instant lastFailureTime = Instant.MIN;
        
        public CircuitBreakerConfig(int threshold, Duration timeout) {
            this.threshold = threshold;
            this.timeout = timeout;
        }
        
        public boolean isOpen() {
            if (failureCount >= threshold) {
                return Instant.now().isBefore(lastFailureTime.plus(timeout));
            }
            return false;
        }
        
        public void onSuccess() {
            failureCount = 0;
        }
        
        public void onFailure() {
            failureCount++;
            lastFailureTime = Instant.now();
        }
    }
}
package app.js.structured;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

public class ImprovedBusinessService {
    private static final Logger logger = LoggerFactory.getLogger(ImprovedBusinessService.class);
    private final ScopedRequestHandler scopedHandler = new ScopedRequestHandler();

    private final EnhancedCircuitBreakerConfig dbCircuitBreaker = 
        new EnhancedCircuitBreakerConfig("DB_SERVICE", 3, Duration.ofSeconds(30));

    public String callProtectedService(String request) throws Exception {
        logger.info("Calling protected service with request: {}", request);

        if (dbCircuitBreaker.isOpen()) {
            String message = String.format("Circuit breaker is OPEN for %s - failing fast (failures: %d/%d, next retry in: %s)", 
                dbCircuitBreaker.getServiceName(),
                dbCircuitBreaker.getFailureCount(),
                dbCircuitBreaker.getThreshold(),
                dbCircuitBreaker.getTimeUntilRetry());
            
            logger.warn(message);
            throw new RuntimeException(message);
        }
        
        try {
            String result = scopedHandler.runInScope(() -> callUnreliableService(request));
            dbCircuitBreaker.onSuccess();
            logger.info("Protected service call succeeded, circuit breaker reset");
            return result;
        } catch (Exception e) {
            dbCircuitBreaker.onFailure();
            logger.error("Protected service call failed (failure {}/{}): {}", 
                dbCircuitBreaker.getFailureCount(), 
                dbCircuitBreaker.getThreshold(), 
                e.getMessage());
            throw e;
        }
    }

    public String performRetryableOperation(String operation) throws Exception {
        logger.info("Performing retryable operation: {}", operation);
        
        return scopedHandler.runWithRetry(
            () -> unstableExternalService(operation),
            3,
            Duration.ofMillis(500)
        );
    }

    private String callUnreliableService(String request) throws Exception {
        Thread.sleep(100);
        if (Math.random() < 0.4) {
            throw new RuntimeException("Unreliable service failed");
        }
        return "Unreliable-" + request;
    }
    
    private String unstableExternalService(String operation) throws Exception {
        Thread.sleep(100);
        if (Math.random() < 0.6) {
            throw new RuntimeException("External service failed");
        }
        return "External-" + operation;
    }

    public static class EnhancedCircuitBreakerConfig {
        private final String serviceName;
        private int failureCount = 0;
        private final int threshold;
        private final Duration timeout;
        private Instant lastFailureTime = Instant.MIN;
        
        public EnhancedCircuitBreakerConfig(String serviceName, int threshold, Duration timeout) {
            this.serviceName = serviceName;
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
            lastFailureTime = Instant.MIN;
        }
        
        public void onFailure() {
            failureCount++;
            lastFailureTime = Instant.now();
        }
        
        public String getServiceName() { return serviceName; }
        public int getFailureCount() { return failureCount; }
        public int getThreshold() { return threshold; }
        
        public String getTimeUntilRetry() {
            if (!isOpen()) return "N/A";
            
            Duration remaining = Duration.between(Instant.now(), lastFailureTime.plus(timeout));
            return remaining.toSeconds() + "s";
        }
    }
}
package app.js.structured;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;

public class BusinessService {
    private static final Logger logger = LoggerFactory.getLogger(BusinessService.class);
    private final ScopedRequestHandler scopedHandler = new ScopedRequestHandler();

    private final ScopedRequestHandler.CircuitBreakerConfig dbCircuitBreaker = 
        new ScopedRequestHandler.CircuitBreakerConfig(3, Duration.ofSeconds(30));
    
    private final ScopedRequestHandler.CircuitBreakerConfig apiCircuitBreaker = 
        new ScopedRequestHandler.CircuitBreakerConfig(5, Duration.ofSeconds(60));

    public String fetchUserProfile(String userId) throws Exception {
        logger.info("Fetching user profile for: {}", userId);
        
        var result = scopedHandler.runInParallel(
            () -> fetchUserBasicInfo(userId),
            () -> fetchUserPreferences(userId)
        );
        
        return String.format("User Profile: %s | Preferences: %s", 
            result.result1(), result.result2());
    }

    public String buildDashboard(String userId) throws Exception {
        logger.info("Building dashboard for: {}", userId);
        
        var result = scopedHandler.runInParallel(
            () -> fetchUserStats(userId),
            () -> fetchRecentActivity(userId),
            () -> fetchNotifications(userId)
        );
        
        return String.format("Dashboard: Stats[%s] | Activity[%s] | Notifications[%s]", 
            result.result1(), result.result2(), result.result3());
    }

    public String aggregateServices() throws Exception {
        logger.info("Aggregating multiple services");
        
        var request = ScopedRequestHandler.AggregateRequest.of(
            () -> callAuthService(),
            () -> callUserService(),
            () -> callNotificationService(),
            () -> callAnalyticsService()
        );
        
        var result = scopedHandler.aggregate(request);
        
        return String.format("Aggregated %d services in %dms: %s", 
            result.results().size(), result.durationMs(), result.results());
    }

    public String getCachedData(String key) throws Exception {
        logger.info("Getting cached data for: {}", key);
        
        return scopedHandler.runFirstSuccess(
            () -> getFromL1Cache(key),
            () -> getFromL2Cache(key),
            () -> getFromDatabase(key)
        );
    }

    public String getDataWithFallback(String key) throws Exception {
        logger.info("Getting data with fallback for: {}", key);
        
        return scopedHandler.runWithFallback(
            () -> getFromPrimaryDatabase(key),
            () -> getFromSecondaryDatabase(key)
        );
    }

    public String performRetryableOperation(String operation) throws Exception {
        logger.info("Performing retryable operation: {}", operation);
        
        return scopedHandler.runWithRetry(
            () -> unstableExternalService(operation),
            3,
            Duration.ofMillis(500)
        );
    }

    public String callProtectedService(String request) throws Exception {
        logger.info("Calling protected service with request: {}", request);
        
        return scopedHandler.runWithCircuitBreaker(
            () -> callUnreliableService(request),
            dbCircuitBreaker
        );
    }

    public String performTimedOperation(String operation) throws Exception {
        logger.info("Performing timed operation: {}", operation);
        
        return scopedHandler.runInScopeWithTimeout(
            () -> slowExternalService(operation),
            Duration.ofSeconds(2)
        );
    }

    public String processOrder(String orderId) throws Exception {
        logger.info("Processing order: {}", orderId);

        var validationResult = scopedHandler.runInParallel(
            () -> validatePayment(orderId),
            () -> validateInventory(orderId),
            () -> validateShipping(orderId)
        );

        if (allValidationsPassed(validationResult)) {
            return scopedHandler.runInParallel(
                () -> chargePayment(orderId),
                () -> reserveInventory(orderId),
                () -> scheduleShipping(orderId)
            ).toString();
        }
        
        return "Order validation failed: " + validationResult;
    }

    private String fetchUserBasicInfo(String userId) throws Exception {
        Thread.sleep(100);
        return "BasicInfo-" + userId;
    }
    
    private String fetchUserPreferences(String userId) throws Exception {
        Thread.sleep(80);
        return "Preferences-" + userId;
    }
    
    private String fetchUserStats(String userId) throws Exception {
        Thread.sleep(150);
        return "Stats-" + userId;
    }
    
    private String fetchRecentActivity(String userId) throws Exception {
        Thread.sleep(120);
        return "Activity-" + userId;
    }
    
    private String fetchNotifications(String userId) throws Exception {
        Thread.sleep(90);
        return "Notifications-" + userId;
    }
    
    private String callAuthService() throws Exception {
        Thread.sleep(100);
        return "Auth-OK";
    }
    
    private String callUserService() throws Exception {
        Thread.sleep(150);
        return "User-OK";
    }
    
    private String callNotificationService() throws Exception {
        Thread.sleep(80);
        return "Notification-OK";
    }
    
    private String callAnalyticsService() throws Exception {
        Thread.sleep(200);
        return "Analytics-OK";
    }
    
    private String getFromL1Cache(String key) throws Exception {
        Thread.sleep(10);
        if (Math.random() < 0.3) throw new RuntimeException("L1 cache miss");
        return "L1-" + key;
    }
    
    private String getFromL2Cache(String key) throws Exception {
        Thread.sleep(50);
        if (Math.random() < 0.5) throw new RuntimeException("L2 cache miss");
        return "L2-" + key;
    }
    
    private String getFromDatabase(String key) throws Exception {
        Thread.sleep(200);
        return "DB-" + key;
    }
    
    private String getFromPrimaryDatabase(String key) throws Exception {
        Thread.sleep(100);
        if (Math.random() < 0.3) throw new RuntimeException("Primary DB unavailable");
        return "Primary-" + key;
    }
    
    private String getFromSecondaryDatabase(String key) throws Exception {
        Thread.sleep(150);
        return "Secondary-" + key;
    }
    
    private String unstableExternalService(String operation) throws Exception {
        Thread.sleep(100);
        if (Math.random() < 0.6) throw new RuntimeException("External service failed");
        return "External-" + operation;
    }
    
    private String callUnreliableService(String request) throws Exception {
        Thread.sleep(100);
        if (Math.random() < 0.4) throw new RuntimeException("Unreliable service failed");
        return "Unreliable-" + request;
    }
    
    private String slowExternalService(String operation) throws Exception {
        Thread.sleep(1500);
        return "Slow-" + operation;
    }
    
    private String validatePayment(String orderId) throws Exception {
        Thread.sleep(100);
        return "Payment-Valid-" + orderId;
    }
    
    private String validateInventory(String orderId) throws Exception {
        Thread.sleep(80);
        return "Inventory-Valid-" + orderId;
    }
    
    private String validateShipping(String orderId) throws Exception {
        Thread.sleep(60);
        return "Shipping-Valid-" + orderId;
    }
    
    private String chargePayment(String orderId) throws Exception {
        Thread.sleep(200);
        return "Payment-Charged-" + orderId;
    }
    
    private String reserveInventory(String orderId) throws Exception {
        Thread.sleep(150);
        return "Inventory-Reserved-" + orderId;
    }
    
    private String scheduleShipping(String orderId) throws Exception {
        Thread.sleep(100);
        return "Shipping-Scheduled-" + orderId;
    }
    
    private boolean allValidationsPassed(ScopedRequestHandler.TripleResult<String, String, String> result) {
        return result.result1().contains("Valid") && 
               result.result2().contains("Valid") && 
               result.result3().contains("Valid");
    }
}
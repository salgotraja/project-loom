package app.js.structured;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CircuitBreakerTest {
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerTest.class);
    
    static void main(String[] args) throws Exception {
        BusinessService service = new BusinessService();
        
        logger.info(" Circuit Breaker Test");
        logger.info("========================");

        for (int i = 1; i <= 10; i++) {
            try {
                System.out.printf("Call %d: ", i);
                String result = service.callProtectedService("test-request-" + i);
                logger.info(" SUCCESS - {}", result);
            } catch (Exception e) {
                if (e.getMessage().contains("Circuit breaker is OPEN")) {
                    logger.info("\uD83D\uDEAB CIRCUIT BREAKER OPEN - {}", e.getMessage());
                } else {
                    logger.error(" FAILURE - {}", e.getMessage());
                }
            }

            Thread.sleep(100);
        }
        
        logger.info("\n Waiting for circuit breaker to reset (30 seconds)...");
        Thread.sleep(31000);
        
        logger.info("\n Testing after circuit breaker reset:");
        try {
            String result = service.callProtectedService("test-after-reset");
            logger.info(" SUCCESS after reset - {}", result);
        } catch (Exception e) {
            logger.error(" Still failing - {}", e.getMessage());
        }
    }
}
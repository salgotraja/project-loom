package app.js.structured;

public class EnhancedCircuitBreakerDemo {
    public static void main(String[] args) throws Exception {
        ImprovedBusinessService service = new ImprovedBusinessService();
        
        System.out.println(" Enhanced Circuit Breaker Demo");
        System.out.println("=================================");

        for (int i = 1; i <= 8; i++) {
            System.out.printf("\n--- Call %d ---\n", i);
            try {
                String result = service.callProtectedService("test-request-" + i);
                System.out.println(" SUCCESS: " + result);
            } catch (Exception e) {
                System.out.println(" FAILURE: " + e.getMessage());
            }
            
            Thread.sleep(500);
        }
        
        System.out.println("\n Waiting 5 seconds then testing again...");
        Thread.sleep(5000);
        
        System.out.println("\n--- After 5 seconds ---");
        try {
            String result = service.callProtectedService("test-after-wait");
            System.out.println(" SUCCESS: " + result);
        } catch (Exception e) {
            System.out.println(" FAILURE: " + e.getMessage());
        }
        
        System.out.println("\n Testing retry pattern:");
        try {
            String result = service.performRetryableOperation("important-task");
            System.out.println(" RETRY SUCCESS: " + result);
        } catch (Exception e) {
            System.out.println(" RETRY FAILED: " + e.getMessage());
        }
    }
}
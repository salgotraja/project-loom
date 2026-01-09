package app.js.structured;

import java.util.concurrent.Callable;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ProgressiveHierarchicalDemo {

    public static void main(String[] args) throws Exception {
        System.out.println(" Progressive Results & Hierarchical Task Management Demo");
        System.out.println("=========================================================");

        HierarchicalProgressiveHandler handler = new HierarchicalProgressiveHandler();

        System.out.println("\n Demo 1: Progressive Results Pattern");
        demonstrateProgressiveResults(handler);

        System.out.println("\n Demo 2: Hierarchical Task Management");
        demonstrateHierarchicalTasks(handler);

        System.out.println("\n Demo 3: Combined Progressive + Hierarchical");
        demonstrateCombinedApproach(handler);

        System.out.println("\n Demo 4: Real-world E-commerce Order Processing");
        demonstrateEcommerceWorkflow(handler);

        handler.getMetrics().printReport();

        System.out.println("\n All demonstrations completed!");
    }

    private static void demonstrateProgressiveResults(HierarchicalProgressiveHandler handler) throws Exception {
        System.out.println("   Processing multiple services with real-time progress...");

        List<Callable<String>> tasks = List.of(
                () -> simulateService("UserService", 200, 0.1),
                () -> simulateService("InventoryService", 300, 0.05),
                () -> simulateService("PaymentService", 400, 0.15),
                () -> simulateService("ShippingService", 250, 0.08),
                () -> simulateService("NotificationService", 150, 0.03)
        );

        var request = new HierarchicalProgressiveHandler.ProgressiveRequest<>(
                tasks,
                Duration.ofSeconds(5),
                update -> {
                    if (update.isSuccess()) {
                        System.out.printf("     Task %d completed: %s%n",
                                update.getTaskIndex(), update.getResult());
                    } else {
                        System.out.printf("     Task %d failed: %s%n",
                                update.getTaskIndex(), update.getError().getMessage());
                    }
                }
        );

        var result = handler.executeProgressive(request);

        System.out.printf("   Progressive Results Summary:%n");
        System.out.printf("    - Completed: %d/%d tasks%n",
                result.getResults().size(), tasks.size());
        System.out.printf("    - Duration: %d ms%n", result.getDurationMs());
        System.out.printf("    - Success Rate: %s%n",
                result.isCompleted() ? "100%" : "Partial");
    }

    private static void demonstrateHierarchicalTasks(HierarchicalProgressiveHandler handler) throws Exception {
        System.out.println("   Executing hierarchical task structure...");

        Callable<String> hierarchicalTask = () -> {

            String userData = executeDataGatheringLevel();
            System.out.println("     Level 1 (Data Gathering) completed");

            String businessResult = executeBusinessLogicLevel(userData);
            System.out.println("     Level 2 (Business Logic) completed");

            String finalResult = executeFinalizationLevel(businessResult);
            System.out.println("     Level 3 (Finalization) completed");

            return String.format("Hierarchical[%s -> %s -> %s]", userData, businessResult, finalResult);
        };

        var request = new HierarchicalProgressiveHandler.HierarchicalRequest<>(
                hierarchicalTask,
                9,
                Duration.ofSeconds(10)
        );

        var result = handler.executeHierarchical(request);

        System.out.printf("   Hierarchical Results Summary:%n");
        System.out.printf("    - Result: %s%n", result.getResult());
        System.out.printf("    - Duration: %d ms%n", result.getDurationMs());
        System.out.printf("    - Success: %s%n", result.isSuccessful() ? "Yes" : "No");
    }

    private static void demonstrateCombinedApproach(HierarchicalProgressiveHandler handler) throws Exception {
        System.out.println("   Executing combined progressive + hierarchical approach...");

        var level1 = new HierarchicalProgressiveHandler.CombinedRequest.Level<>(
                "DataPreparation",
                List.of(
                        () -> simulateService("UserProfileService", 150, 0.05),
                        () -> simulateService("UserPreferencesService", 120, 0.03),
                        () -> simulateService("UserHistoryService", 200, 0.08)
                ),
                Duration.ofSeconds(3)
        );

        var level2 = new HierarchicalProgressiveHandler.CombinedRequest.Level<>(
                "BusinessProcessing",
                List.of(
                        () -> simulateService("InventoryCheckService", 250, 0.10),
                        () -> simulateService("PriceCalculationService", 180, 0.06),
                        () -> simulateService("DiscountService", 160, 0.04)
                ),
                Duration.ofSeconds(3)
        );

        var level3 = new HierarchicalProgressiveHandler.CombinedRequest.Level<>(
                "OrderFinalization",
                List.of(
                        () -> simulateService("PaymentProcessingService", 400, 0.15),
                        () -> simulateService("ShippingCalculationService", 220, 0.09),
                        () -> simulateService("OrderConfirmationService", 100, 0.02)
                ),
                Duration.ofSeconds(4)
        );

        var request = new HierarchicalProgressiveHandler.CombinedRequest<>(
                List.of(level1, level2, level3),
                update -> {
                    System.out.printf("     %s: Task %d %s%n",
                            update.getLevelId(),
                            update.getProgressUpdate().getTaskIndex(),
                            update.getProgressUpdate().isSuccess() ? "completed" : "failed"
                    );
                }
        );

        var result = handler.executeCombined(request);

        System.out.printf("   Combined Results Summary:%n");
        System.out.printf("    - Completed Levels: %s%n", result.getCompletedLevels());
        System.out.printf("    - Total Duration: %d ms%n", result.getDurationMs());
        System.out.printf("    - Success: %s%n", result.isSuccessful() ? "Yes" : "No");

        result.getLevelResults().forEach((levelId, levelData) -> {
            Map<String, Object> data = (Map<String, Object>) levelData;
            System.out.printf("    - %s: %d ms, %s%n",
                    levelId,
                    (Long) data.get("duration"),
                    (Boolean) data.get("completed") ? "" : ""
            );
        });
    }

    private static void demonstrateEcommerceWorkflow(HierarchicalProgressiveHandler handler) throws Exception {
        System.out.println("   Processing complete e-commerce order workflow...");

        EcommerceOrderProcessor processor = new EcommerceOrderProcessor(handler);
        String orderId = "ORD-" + System.currentTimeMillis();
        String userId = "user-12345";

        System.out.printf("     Processing order: %s for user: %s%n", orderId, userId);

        var orderResult = processor.processOrder(orderId, userId);

        System.out.printf("   E-commerce Order Results:%n");
        System.out.printf("    - Order ID: %s%n", orderId);
        System.out.printf("    - Processing Time: %d ms%n", orderResult.getDurationMs());
        System.out.printf("    - Status: %s%n", orderResult.isSuccessful() ? " Success" : " Failed");
        System.out.printf("    - Completed Phases: %s%n", orderResult.getCompletedLevels());

        if (orderResult.isSuccessful()) {
            System.out.println("     Order processed successfully with real-time progress tracking!");
        }
    }

    private static String simulateService(String serviceName, int delayMs, double failureRate) {
        try {
            Thread.sleep(delayMs);

            if (Math.random() < failureRate) {
                throw new RuntimeException("Service " + serviceName + " temporarily unavailable");
            }
            
            return serviceName + " response (processed in " + delayMs + "ms)";
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Service " + serviceName + " interrupted", e);
        }
    }

    private static String executeDataGatheringLevel() throws Exception {

        Thread.sleep(200);
        Thread.sleep(150);
        Thread.sleep(180);
        return "DataGathered";
    }

    private static String executeBusinessLogicLevel(String userData) throws Exception {

        Thread.sleep(250);
        Thread.sleep(200);
        Thread.sleep(180);
        return "BusinessProcessed";
    }

    private static String executeFinalizationLevel(String businessResult) throws Exception {

        Thread.sleep(300);
        Thread.sleep(150);
        Thread.sleep(100);
        return "OrderFinalized";
    }

    private static class EcommerceOrderProcessor {
        private final HierarchicalProgressiveHandler handler;

        public EcommerceOrderProcessor(HierarchicalProgressiveHandler handler) {
            this.handler = handler;
        }

        public HierarchicalProgressiveHandler.CombinedResult<Object> processOrder(String orderId, String userId) throws Exception {

            var phase1 = new HierarchicalProgressiveHandler.CombinedRequest.Level<>(
                    "UserDataCollection",
                    List.of(
                            () -> fetchUserProfile(userId),
                            () -> fetchUserPreferences(userId),
                            () -> fetchUserPaymentMethods(userId),
                            () -> fetchUserAddresses(userId)
                    ),
                    Duration.ofSeconds(2)
            );

            var phase2 = new HierarchicalProgressiveHandler.CombinedRequest.Level<>(
                    "ProductInventoryProcessing",
                    List.of(
                            () -> validateProductAvailability(orderId),
                            () -> calculatePricing(orderId),
                            () -> applyDiscounts(orderId, userId),
                            () -> reserveInventory(orderId)
                    ),
                    Duration.ofSeconds(3)
            );

            var phase3 = new HierarchicalProgressiveHandler.CombinedRequest.Level<>(
                    "PaymentShippingProcessing",
                    List.of(
                            () -> processPayment(orderId, userId),
                            () -> calculateShipping(orderId, userId),
                            () -> scheduleDelivery(orderId)
                    ),
                    Duration.ofSeconds(4)
            );

            var phase4 = new HierarchicalProgressiveHandler.CombinedRequest.Level<>(
                    "OrderFinalization",
                    List.of(
                            () -> generateOrderConfirmation(orderId),
                            () -> sendConfirmationEmail(userId),
                            () -> updateInventorySystem(orderId),
                            () -> logOrderAnalytics(orderId)
                    ),
                    Duration.ofSeconds(2)
            );

            var request = new HierarchicalProgressiveHandler.CombinedRequest<>(
                    List.of(phase1, phase2, phase3, phase4),
                    update -> {
                        String phase = update.getLevelId();
                        int taskIndex = update.getProgressUpdate().getTaskIndex();
                        boolean success = update.getProgressUpdate().isSuccess();

                        System.out.printf("       %s - Step %d: %s%n",
                                phase, taskIndex + 1, success ? "" : "");
                    }
            );

            return handler.executeCombined(request);
        }

        private String fetchUserProfile(String userId) throws Exception {
            Thread.sleep(120);
            return "UserProfile[" + userId + "]";
        }

        private String fetchUserPreferences(String userId) throws Exception {
            Thread.sleep(80);
            return "UserPreferences[" + userId + "]";
        }

        private String fetchUserPaymentMethods(String userId) throws Exception {
            Thread.sleep(150);
            return "PaymentMethods[" + userId + "]";
        }

        private String fetchUserAddresses(String userId) throws Exception {
            Thread.sleep(100);
            return "UserAddresses[" + userId + "]";
        }

        private String validateProductAvailability(String orderId) throws Exception {
            Thread.sleep(200);
            return "ProductAvailability[" + orderId + "]";
        }

        private String calculatePricing(String orderId) throws Exception {
            Thread.sleep(150);
            return "Pricing[" + orderId + "]";
        }

        private String applyDiscounts(String orderId, String userId) throws Exception {
            Thread.sleep(180);
            return "Discounts[" + orderId + "]";
        }

        private String reserveInventory(String orderId) throws Exception {
            Thread.sleep(220);
            return "InventoryReserved[" + orderId + "]";
        }

        private String processPayment(String orderId, String userId) throws Exception {
            Thread.sleep(350);
            if (Math.random() < 0.05) {
                throw new RuntimeException("Payment declined");
            }
            return "PaymentProcessed[" + orderId + "]";
        }

        private String calculateShipping(String orderId, String userId) throws Exception {
            Thread.sleep(200);
            return "ShippingCalculated[" + orderId + "]";
        }

        private String scheduleDelivery(String orderId) throws Exception {
            Thread.sleep(120);
            return "DeliveryScheduled[" + orderId + "]";
        }

        private String generateOrderConfirmation(String orderId) throws Exception {
            Thread.sleep(80);
            return "OrderConfirmation[" + orderId + "]";
        }

        private String sendConfirmationEmail(String userId) throws Exception {
            Thread.sleep(100);
            return "EmailSent[" + userId + "]";
        }

        private String updateInventorySystem(String orderId) throws Exception {
            Thread.sleep(150);
            return "InventoryUpdated[" + orderId + "]";
        }

        private String logOrderAnalytics(String orderId) throws Exception {
            Thread.sleep(50);
            return "AnalyticsLogged[" + orderId + "]";
        }
    }
}
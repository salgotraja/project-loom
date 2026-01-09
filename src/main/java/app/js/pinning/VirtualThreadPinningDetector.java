package app.js.pinning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.Executors;
import java.time.Duration;
import java.time.Instant;

public class VirtualThreadPinningDetector {
    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadPinningDetector.class);
    
    private static final Object SYNC_LOCK = new Object();
    private static final ReentrantLock REENTRANT_LOCK = new ReentrantLock();
    
    static void main(String[] args) throws InterruptedException {
        logger.info(" Virtual Thread Pinning Detection Demo");
        logger.info("=========================================");
        logger.info("Run with: -Djdk.tracePinnedThreads=full");

        logger.info(" Test 1: Synchronized blocks (CAUSES PINNING)");
        testSynchronizedPinning();
        
        Thread.sleep(1000);

        logger.info("\n Test 2: ReentrantLock (NO PINNING)");
        testReentrantLockNoPinning();
        
        Thread.sleep(1000);

        logger.info("\n Test 3: Heavy synchronized load (CAUSES PINNING)");
        testHeavySynchronizedLoad();
        
        Thread.sleep(1000);

        logger.info("\n Test 4: Optimized with ReentrantLock (NO PINNING)");
        testOptimizedWithReentrantLock();
        
        Thread.sleep(1000);

        logger.info("\n Test 5: Pinning detection and metrics");
        testPinningMetrics();
    }
    
    private static void testSynchronizedPinning() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(10);
        
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            Thread.startVirtualThread(() -> {
                try {
                    synchronizedWork(taskId);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        logger.info("Synchronized test completed");
    }
    
    private static void testReentrantLockNoPinning() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(10);
        
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            Thread.startVirtualThread(() -> {
                try {
                    reentrantLockWork(taskId);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        logger.info("ReentrantLock test completed");
    }
    
    private static void testHeavySynchronizedLoad() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(100);
        Instant start = Instant.now();
        
        for (int i = 0; i < 100; i++) {
            final int taskId = i;
            Thread.startVirtualThread(() -> {
                try {
                    heavySynchronizedWork(taskId);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        Duration duration = Duration.between(start, Instant.now());
        System.out.printf("Heavy synchronized load completed in %dms%n", duration.toMillis());
    }
    
    private static void testOptimizedWithReentrantLock() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(100);
        Instant start = Instant.now();
        
        for (int i = 0; i < 100; i++) {
            final int taskId = i;
            Thread.startVirtualThread(() -> {
                try {
                    optimizedReentrantLockWork(taskId);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        Duration duration = Duration.between(start, Instant.now());
        System.out.printf("Optimized ReentrantLock completed in %dms%n", duration.toMillis());
    }
    
    private static void testPinningMetrics() throws InterruptedException {
        PinningMetrics metrics = new PinningMetrics();

        CountDownLatch latch = new CountDownLatch(50);
        
        for (int i = 0; i < 25; i++) {
            final int taskId = i;
            Thread.startVirtualThread(() -> {
                try {
                    metrics.trackOperation("synchronized", () -> synchronizedWork(taskId));
                } finally {
                    latch.countDown();
                }
            });
        }
        
        for (int i = 0; i < 25; i++) {
            final int taskId = i;
            Thread.startVirtualThread(() -> {
                try {
                    metrics.trackOperation("reentrant", () -> reentrantLockWork(taskId));
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        metrics.printReport();
    }

    private static void synchronizedWork(int taskId) {
        synchronized (SYNC_LOCK) {
            try {
                System.out.printf(" Synchronized task %d on thread %s%n", 
                                taskId, Thread.currentThread().getName());
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private static void reentrantLockWork(int taskId) {
        REENTRANT_LOCK.lock();
        try {
            System.out.printf(" ReentrantLock task %d on thread %s%n", 
                            taskId, Thread.currentThread().getName());
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            REENTRANT_LOCK.unlock();
        }
    }
    
    private static void heavySynchronizedWork(int taskId) {
        synchronized (SYNC_LOCK) {
            try {
                for (int i = 0; i < 1000; i++) {
                    Math.sin(i);
                }
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private static void optimizedReentrantLockWork(int taskId) {
        REENTRANT_LOCK.lock();
        try {
            for (int i = 0; i < 1000; i++) {
                Math.sin(i);
            }
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            REENTRANT_LOCK.unlock();
        }
    }

    private static class PinningMetrics {
        private long synchronizedCount = 0;
        private long reentrantCount = 0;
        private long synchronizedTime = 0;
        private long reentrantTime = 0;
        
        public void trackOperation(String type, Runnable operation) {
            long start = System.nanoTime();
            operation.run();
            long duration = System.nanoTime() - start;
            
            if ("synchronized".equals(type)) {
                synchronizedCount++;
                synchronizedTime += duration;
            } else if ("reentrant".equals(type)) {
                reentrantCount++;
                reentrantTime += duration;
            }
        }
        
        public void printReport() {
            logger.info("\n Pinning Metrics Report:");
            logger.info("=========================");
            System.out.printf("Synchronized operations: %d (avg: %.2fms)%n", 
                            synchronizedCount, 
                            synchronizedTime / 1_000_000.0 / synchronizedCount);
            System.out.printf("ReentrantLock operations: %d (avg: %.2fms)%n", 
                            reentrantCount, 
                            reentrantTime / 1_000_000.0 / reentrantCount);
            
            if (synchronizedTime > reentrantTime) {
                double factor = (double) synchronizedTime / reentrantTime;
                System.out.printf("  Synchronized is %.2fx slower (likely due to pinning)%n", factor);
            }
        }
    }
}
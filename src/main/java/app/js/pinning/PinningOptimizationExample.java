package app.js.pinning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CountDownLatch;
import java.util.Map;
import java.time.Duration;
import java.time.Instant;

public class PinningOptimizationExample {
    private static final Logger logger = LoggerFactory.getLogger(PinningOptimizationExample.class);

    private static final Map<String, String> synchronizedCache = new ConcurrentHashMap<>();
    private static final Map<String, String> optimizedCache = new ConcurrentHashMap<>();
    private static final Object syncLock = new Object();
    private static final ReentrantLock reentrantLock = new ReentrantLock();
    private static final StampedLock stampedLock = new StampedLock();
    
    private static volatile int counter = 0;
    
    static void main(String[] args) throws InterruptedException {
        logger.info(" Virtual Thread Pinning Optimization Examples");
        logger.info("===============================================");
        logger.info("Run with: -Djdk.tracePinnedThreads=full");

        warmUp();

        logger.info(" Test 1: Synchronized vs ReentrantLock");
        compareBasicLocking();

        logger.info("\n Test 2: Synchronized Cache vs ConcurrentHashMap");
        compareCacheImplementations();

        logger.info("\n Test 3: Reader-Writer Lock Performance");
        compareReaderWriterLocks();

        logger.info("\n Test 4: Producer-Consumer Pattern");
        compareProducerConsumer();
        
        logger.info("\n All optimization tests completed");
    }
    
    private static void warmUp() throws InterruptedException {
        logger.info(" Warming up JVM...");
        CountDownLatch latch = new CountDownLatch(100);
        
        for (int i = 0; i < 100; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        logger.info(" Warmup completed\n");
    }
    
    private static void compareBasicLocking() throws InterruptedException {
        final int TASKS = 1000;

        Instant start = Instant.now();
        CountDownLatch latch1 = new CountDownLatch(TASKS);
        
        for (int i = 0; i < TASKS; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    synchronizedIncrement();
                } finally {
                    latch1.countDown();
                }
            });
        }
        
        latch1.await();
        Duration syncTime = Duration.between(start, Instant.now());

        counter = 0;

        start = Instant.now();
        CountDownLatch latch2 = new CountDownLatch(TASKS);
        
        for (int i = 0; i < TASKS; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    reentrantLockIncrement();
                } finally {
                    latch2.countDown();
                }
            });
        }
        
        latch2.await();
        Duration reentrantTime = Duration.between(start, Instant.now());
        
        System.out.printf("  Synchronized: %dms (pinning)%n", syncTime.toMillis());
        System.out.printf("  ReentrantLock: %dms (no pinning)%n", reentrantTime.toMillis());
        
        if (syncTime.toMillis() > reentrantTime.toMillis()) {
            double factor = (double) syncTime.toMillis() / reentrantTime.toMillis();
            System.out.printf("   ReentrantLock is %.2fx faster%n", factor);
        }
    }
    
    private static void compareCacheImplementations() throws InterruptedException {
        final int OPERATIONS = 500;

        Instant start = Instant.now();
        CountDownLatch latch1 = new CountDownLatch(OPERATIONS);
        
        for (int i = 0; i < OPERATIONS; i++) {
            final int taskId = i;
            Thread.startVirtualThread(() -> {
                try {
                    synchronizedCacheOperation(taskId);
                } finally {
                    latch1.countDown();
                }
            });
        }
        
        latch1.await();
        Duration syncCacheTime = Duration.between(start, Instant.now());

        start = Instant.now();
        CountDownLatch latch2 = new CountDownLatch(OPERATIONS);
        
        for (int i = 0; i < OPERATIONS; i++) {
            final int taskId = i;
            Thread.startVirtualThread(() -> {
                try {
                    concurrentCacheOperation(taskId);
                } finally {
                    latch2.countDown();
                }
            });
        }
        
        latch2.await();
        Duration concurrentCacheTime = Duration.between(start, Instant.now());
        
        System.out.printf("  Synchronized cache: %dms (pinning)%n", syncCacheTime.toMillis());
        System.out.printf("  ConcurrentHashMap: %dms (no pinning)%n", concurrentCacheTime.toMillis());
        
        if (syncCacheTime.toMillis() > concurrentCacheTime.toMillis()) {
            double factor = (double) syncCacheTime.toMillis() / concurrentCacheTime.toMillis();
            System.out.printf("   ConcurrentHashMap is %.2fx faster%n", factor);
        }
    }
    
    private static void compareReaderWriterLocks() throws InterruptedException {
        final int READERS = 800;
        final int WRITERS = 200;

        Instant start = Instant.now();
        CountDownLatch latch1 = new CountDownLatch(READERS + WRITERS);
        
        for (int i = 0; i < READERS; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    reentrantLockRead();
                } finally {
                    latch1.countDown();
                }
            });
        }
        
        for (int i = 0; i < WRITERS; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    reentrantLockWrite();
                } finally {
                    latch1.countDown();
                }
            });
        }
        
        latch1.await();
        Duration reentrantTime = Duration.between(start, Instant.now());

        start = Instant.now();
        CountDownLatch latch2 = new CountDownLatch(READERS + WRITERS);
        
        for (int i = 0; i < READERS; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    stampedLockRead();
                } finally {
                    latch2.countDown();
                }
            });
        }
        
        for (int i = 0; i < WRITERS; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    stampedLockWrite();
                } finally {
                    latch2.countDown();
                }
            });
        }
        
        latch2.await();
        Duration stampedTime = Duration.between(start, Instant.now());
        
        System.out.printf("  ReentrantLock: %dms%n", reentrantTime.toMillis());
        System.out.printf("  StampedLock: %dms%n", stampedTime.toMillis());
        
        if (reentrantTime.toMillis() > stampedTime.toMillis()) {
            double factor = (double) reentrantTime.toMillis() / stampedTime.toMillis();
            System.out.printf("   StampedLock is %.2fx faster for read-heavy workloads%n", factor);
        }
    }
    
    private static void compareProducerConsumer() throws InterruptedException {
        logger.info("   Producer-Consumer pattern optimization");

        ProducerConsumerOptimized optimizer = new ProducerConsumerOptimized();
        
        Instant start = Instant.now();
        CountDownLatch latch = new CountDownLatch(200);

        for (int i = 0; i < 50; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    optimizer.produce();
                } finally {
                    latch.countDown();
                }
            });
        }

        for (int i = 0; i < 150; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    optimizer.consume();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        Duration duration = Duration.between(start, Instant.now());
        
        System.out.printf("  Producer-Consumer completed in %dms%n", duration.toMillis());
        System.out.printf("  Items produced: %d, consumed: %d%n", 
                         optimizer.getProducedCount(), optimizer.getConsumedCount());
    }

    private static void synchronizedIncrement() {
        synchronized (syncLock) {
            counter++;
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private static void synchronizedCacheOperation(int taskId) {
        synchronized (syncLock) {
            try {
                synchronizedCache.put("key-" + taskId, "value-" + taskId);
                Thread.sleep(2);
                synchronizedCache.get("key-" + taskId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void reentrantLockIncrement() {
        reentrantLock.lock();
        try {
            counter++;
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            reentrantLock.unlock();
        }
    }
    
    private static void concurrentCacheOperation(int taskId) {
        try {
            optimizedCache.put("key-" + taskId, "value-" + taskId);
            Thread.sleep(2);
            optimizedCache.get("key-" + taskId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private static void reentrantLockRead() {
        reentrantLock.lock();
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            reentrantLock.unlock();
        }
    }
    
    private static void reentrantLockWrite() {
        reentrantLock.lock();
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            reentrantLock.unlock();
        }
    }
    
    private static void stampedLockRead() {
        long stamp = stampedLock.readLock();
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stampedLock.unlockRead(stamp);
        }
    }
    
    private static void stampedLockWrite() {
        long stamp = stampedLock.writeLock();
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stampedLock.unlockWrite(stamp);
        }
    }

    private static class ProducerConsumerOptimized {
        private final java.util.concurrent.BlockingQueue<String> queue = 
            new java.util.concurrent.ArrayBlockingQueue<>(100);
        private final java.util.concurrent.atomic.AtomicInteger producedCount = 
            new java.util.concurrent.atomic.AtomicInteger(0);
        private final java.util.concurrent.atomic.AtomicInteger consumedCount = 
            new java.util.concurrent.atomic.AtomicInteger(0);
        
        public void produce() {
            try {
                String item = "item-" + producedCount.incrementAndGet();
                queue.put(item);
                Thread.sleep(ThreadLocalRandom.current().nextInt(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        public void consume() {
            try {
                String item = queue.take();
                consumedCount.incrementAndGet();
                Thread.sleep(ThreadLocalRandom.current().nextInt(3));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        public int getProducedCount() { return producedCount.get(); }
        public int getConsumedCount() { return consumedCount.get(); }
    }
}
package app.js.monitoring;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class JvmMonitoringService {
    private static final Logger logger = LoggerFactory.getLogger(JvmMonitoringService.class);
    public static final int PORT = 8083;
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private static final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

    static void main(String[] args) throws Exception {
        startJFRProfiling();
        
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/metrics", exchange -> {
            String metrics = generatePrometheusMetrics();
            sendResponse(exchange, metrics);
        });

        server.createContext("/jvm-info", exchange -> {
            String info = generateJvmInfo();
            sendResponse(exchange, info);
        });

        server.start();
        logger.debug(" JVM Monitoring Service started on port {}", PORT);
        logger.info("Endpoints:");
        logger.info("  GET /metrics    - Prometheus metrics");
        logger.info("  GET /jvm-info   - JVM information");
    }

    private static void startJFRProfiling() {
        logger.info("JFR Profiling enabled. Use JVM args: -XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=app.jfr");
    }

    private static String generatePrometheusMetrics() {
        StringBuilder metrics = new StringBuilder();
        logger.info("Prometheus metrics endpoint started");
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        metrics.append("# HELP jvm_memory_used_bytes Used memory in bytes\n");
        metrics.append("# TYPE jvm_memory_used_bytes gauge\n");
        metrics.append("jvm_memory_used_bytes{area=\"heap\"} ").append(heapUsage.getUsed()).append("\n");
        
        metrics.append("# HELP jvm_memory_max_bytes Maximum memory in bytes\n");
        metrics.append("# TYPE jvm_memory_max_bytes gauge\n");
        metrics.append("jvm_memory_max_bytes{area=\"heap\"} ").append(heapUsage.getMax()).append("\n");

        ManagementFactory.getGarbageCollectorMXBeans().forEach(gc -> {
            metrics.append("# HELP jvm_gc_collection_seconds Time spent in GC\n");
            metrics.append("# TYPE jvm_gc_collection_seconds counter\n");
            metrics.append("jvm_gc_collection_seconds{gc=\"").append(gc.getName()).append("\"} ")
                    .append(gc.getCollectionTime() / 1000.0).append("\n");
        });
        
        return metrics.toString();
    }

    private static String generateJvmInfo() {
        StringBuilder info = new StringBuilder();
        info.append("JVM Information:\n");
        info.append("================\n");
        info.append("Java Version: ").append(System.getProperty("java.version")).append("\n");
        info.append("JVM Name: ").append(runtimeBean.getVmName()).append("\n");
        info.append("JVM Version: ").append(runtimeBean.getVmVersion()).append("\n");
        info.append("Uptime: ").append(runtimeBean.getUptime() / 1000).append(" seconds\n");
        info.append("Available Processors: ").append(Runtime.getRuntime().availableProcessors()).append("\n");
        
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        info.append("Heap Usage: ").append(heapUsage.getUsed() / 1024 / 1024).append("MB / ")
                .append(heapUsage.getMax() / 1024 / 1024).append("MB\n");
        
        return info.toString();
    }

    private static void sendResponse(HttpExchange exchange, String response) {
        try {
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();
        } catch (IOException e) {
            logger.error("Failed to send response: {}", e.getMessage());
        }
    }
}

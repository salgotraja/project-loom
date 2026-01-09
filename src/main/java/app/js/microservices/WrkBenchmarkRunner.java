package app.js.microservices;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

public class WrkBenchmarkRunner {
    private static final Logger logger = LoggerFactory.getLogger(WrkBenchmarkRunner.class);
    
    private static final String[] ENDPOINTS = {"/compute", "/block", "/file"};
    private static final String[] ENDPOINT_NAMES = {"CPU-Intensive", "I/O Blocking", "File Reading"};
    
    static void main(String[] args) {
        logger.info(" Automated wrk Benchmarking Tool");
        logger.info("==================================");

        if (!isWrkInstalled()) {
            logger.error(" wrk is not installed or not in PATH");
            logger.error("Please install wrk: https://github.com/wg/wrk");
            logger.error("Ubuntu: sudo apt-get install wrk");
            logger.error("macOS: brew install wrk");
            return;
        }
        
        logger.info(" wrk is installed and ready");

        if (!isServerRunning(8080)) {
            logger.error(" Virtual Thread Server (port 8080) is not running");
            logger.error("Please start: java app.js.microservices.VirtualThreadMicroservice");
            return;
        }
        
        if (!isServerRunning(8081)) {
            logger.error(" Platform Thread Server (port 8081) is not running");
            logger.error("Please start: java app.js.microservices.PlatformThreadMicroservice");
            return;
        }
        
        logger.info(" Both servers are running. Starting benchmarks...\n");

        benchmarkServer("Virtual Thread Server", 8080);
        logger.info("\n{}\n", "=".repeat(80));
        benchmarkServer("Platform Thread Server", 8081);

        logger.info("\n{}", "=".repeat(80));
        logger.info(" FINAL METRICS COMPARISON");
        logger.info("=".repeat(80));
        
        logger.info("\n--- Virtual Thread Server Metrics ---");
        printMetrics(8080);
        
        logger.info("\n--- Platform Thread Server Metrics ---");
        printMetrics(8081);
        
        logger.info("\n Benchmarking completed!");
    }
    
    private static boolean isWrkInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder("wrk", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }

            return line != null && (line.contains("wrk") || line.contains("version"));
            
        } catch (Exception e) {
            logger.error("Debug - wrk check failed: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean isServerRunning(int port) {
        try {
            HttpResponse<String> response;
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/health"))
                        .timeout(java.time.Duration.ofSeconds(5))
                        .GET()
                        .build();

                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            }
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
    
    private static void benchmarkServer(String serverName, int port) {
        logger.info("\uD83D\uDD25 Benchmarking {} (Port {})", serverName, port);
        logger.info("-".repeat(60));
        
        for (int i = 0; i < ENDPOINTS.length; i++) {
            String endpoint = ENDPOINTS[i];
            String endpointName = ENDPOINT_NAMES[i];
            
            logger.info("\n Testing " + endpointName + " endpoint: " + endpoint);
            logger.info("Command: wrk -t8 -c1000 -d30s http://localhost:" + port + endpoint);
            logger.info("-".repeat(40));
            
            try {
                String[] command = {
                    "wrk", 
                    "-t8",
                    "-c1000",
                    "-d30s",
                    "http://localhost:" + port + endpoint
                };
                
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info(line);
                }
                
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    logger.error(" wrk command failed with exit code: {}", exitCode);
                }

                logger.info("\n Waiting 5 seconds before next test...");
                Thread.sleep(5000);
                
            } catch (Exception e) {
                logger.error(" Error running wrk: {}", e.getMessage());
                logger.error("Debug - Stack trace:", e);
            }
        }
    }
    
    private static void printMetrics(int port) {
        try {
            HttpResponse<String> response;
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/metrics"))
                        .timeout(java.time.Duration.ofSeconds(5))
                        .GET()
                        .build();

                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            }
            if (response.statusCode() == 200) {
                logger.info(response.body());
            } else {
                logger.error("Failed to get metrics from port {}", port);
            }
        } catch (Exception e) {
            logger.error("Error getting metrics: {}", e.getMessage());
        }
    }
}
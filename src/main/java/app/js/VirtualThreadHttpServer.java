package app.js;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class VirtualThreadHttpServer {
    static void main(String[] args) throws IOException {
        System.out.println("Virtual Thread HTTP Server");
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        System.out.println("Listening on port 8080");

        server.createContext("/hello", exchange -> {
            try {
                System.out.println("Received request on virtual thread: " + Thread.currentThread().getName());
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Thread interrupted: " + e.getMessage());
            }

            String response = "Hello from virtual thread!";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
            exchange.close();
        });

        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("Press Ctrl+C to stop the server.");
    }
}

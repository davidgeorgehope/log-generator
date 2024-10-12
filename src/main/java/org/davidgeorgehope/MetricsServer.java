package org.davidgeorgehope;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;

public abstract class MetricsServer {
    protected final int port;
    protected int activeConnections = 0;
    protected int acceptedConnections = 0;
    protected int handledConnections = 0;
    protected int requests = 0;
    protected int reading = 0;
    protected int writing = 0;
    protected int waiting = 0;

    public MetricsServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/nginx_status", exchange -> {
                String response = generateNginxStatus();
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });

            server.setExecutor(null);
            server.start();

            System.out.println(getServerName() + " Metrics Server started on port " + port);

            // Update metrics every second
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                this::updateMetrics,
                0,
                1,
                TimeUnit.SECONDS
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected abstract String getServerName();

    protected void updateMetrics() {
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        // Update reading, writing, and waiting
        reading = rand.nextInt(1, 10);
        writing = rand.nextInt(1, 50);
        waiting = rand.nextInt(1, 100);

        // Active connections is the sum of reading, writing, and waiting
        activeConnections = reading + writing + waiting;

        // Accepted and handled connections should not decrease
        int newAccepted = rand.nextInt(50, 150);
        acceptedConnections += newAccepted;

        // Handled connections should be less than or equal to accepted connections
        int newHandled = rand.nextInt(30, newAccepted);
        handledConnections += newHandled;

        // Requests should increase
        requests += rand.nextInt(100, 300);

        // Ensure handledConnections does not exceed acceptedConnections
        if (handledConnections > acceptedConnections) {
            handledConnections = acceptedConnections;
        }
    }

    protected String generateNginxStatus() {
        return String.format(
            "Active connections: %d\n" +
            "server accepts handled requests\n" +
            " %d %d %d\n" +
            "Reading: %d Writing: %d Waiting: %d\n",
            activeConnections, acceptedConnections, handledConnections, requests,
            reading, writing, waiting
        );
    }
}

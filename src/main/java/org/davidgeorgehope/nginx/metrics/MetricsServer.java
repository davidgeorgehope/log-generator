package org.davidgeorgehope.nginx.metrics;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;

// Import the AnomalyConfig class
import org.davidgeorgehope.AnomalyConfig;

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

        if (AnomalyConfig.isInduceDatabaseOutage()) {
            // Simulate metrics during database outage
            // Increase waiting connections due to stalled requests
            reading = rand.nextInt(1, 5);
            writing = rand.nextInt(1, 10);
            waiting = rand.nextInt(100, 200); // Significantly increased waiting

            // Active connections increase due to accumulation
            activeConnections = reading + writing + waiting;

            // Accepted connections continue normally
            int newAccepted = rand.nextInt(50, 100);
            acceptedConnections += newAccepted;

            // Handled connections decrease as processing stalls
            int newHandled = rand.nextInt(10, 30);
            handledConnections += newHandled;

            // Requests might increase due to retries or timeouts
            requests += rand.nextInt(300, 500);

            // Ensure handledConnections does not exceed acceptedConnections
            if (handledConnections > acceptedConnections) {
                handledConnections = acceptedConnections;
            }
        } else {
            // Normal operation metrics update
            reading = rand.nextInt(1, 10);
            writing = rand.nextInt(1, 50);
            waiting = rand.nextInt(1, 100);

            activeConnections = reading + writing + waiting;

            int newAccepted = rand.nextInt(50, 150);
            acceptedConnections += newAccepted;

            int newHandled = rand.nextInt(30, newAccepted);
            handledConnections += newHandled;

            requests += rand.nextInt(100, 300);

            if (handledConnections > acceptedConnections) {
                handledConnections = acceptedConnections;
            }
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

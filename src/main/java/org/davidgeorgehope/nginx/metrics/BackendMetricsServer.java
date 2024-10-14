package org.davidgeorgehope.nginx.metrics;

import java.util.Random;

public class BackendMetricsServer extends MetricsServer {

    public BackendMetricsServer(int port) {
        super(port);
    }

    @Override
    protected String getServerName() {
        return "Backend";
    }

    @Override
    protected void updateMetrics() {
        Random rand = new Random();
        activeConnections = rand.nextInt(500) + 100;
        acceptedConnections += rand.nextInt(100) + 50;
        handledConnections += rand.nextInt(100) + 50;
        requests += rand.nextInt(200) + 100;
    }
}

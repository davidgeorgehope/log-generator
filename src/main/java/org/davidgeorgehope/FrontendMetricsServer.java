package org.davidgeorgehope;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FrontendMetricsServer extends MetricsServer {

    public FrontendMetricsServer(int port) {
        super(port);
    }

    @Override
    protected String getServerName() {
        return "Frontend";
    }

    @Override
    protected void updateMetrics() {
        Random rand = new Random();
        activeConnections = rand.nextInt(1000) + 200;
        acceptedConnections += rand.nextInt(200) + 100;
        handledConnections += rand.nextInt(200) + 100;
        requests += rand.nextInt(400) + 200;
    }
}

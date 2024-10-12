package org.davidgeorgehope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.*;

public class NginxLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(NginxLogGenerator.class);

    public static void main(String[] args) {
        String frontendLogDir = "logs/frontend";
        String backendLogDir = "logs/backend";

        // Load anomaly configuration from environment variables
        loadAnomalyConfig();

        // Create directories if they don't exist
        new File(frontendLogDir).mkdirs();
        new File(backendLogDir).mkdirs();

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);

        UserSessionManager userSessionManager = new UserSessionManager();

        // Generate frontend access logs continuously
        executor.scheduleAtFixedRate(() -> {
            int logsToGenerate = LogGeneratorUtils.getLogsToGenerate();
            AccessLogGenerator.generateAccessLogs(logsToGenerate, frontendLogDir + "/access.log", true, userSessionManager);
        }, 0, 1, TimeUnit.SECONDS);

        // Generate backend access logs continuously
        executor.scheduleAtFixedRate(() -> {
            int logsToGenerate = LogGeneratorUtils.getLogsToGenerate();
            AccessLogGenerator.generateAccessLogs(logsToGenerate, backendLogDir + "/access.log", false, userSessionManager);
        }, 0, 1, TimeUnit.SECONDS);

        // Generate frontend error logs less frequently
        executor.scheduleAtFixedRate(() -> {
            ErrorLogGenerator.generateErrorLogs(1, frontendLogDir + "/error.log", true);
        }, 0, 5, TimeUnit.SECONDS);

        // Generate backend error logs less frequently
        executor.scheduleAtFixedRate(() -> {
            ErrorLogGenerator.generateErrorLogs(1, backendLogDir + "/error.log", false);
        }, 0, 5, TimeUnit.SECONDS);

        int frontendPort = 8080;
        int backendPort = 8081;

        FrontendMetricsServer frontendServer = new FrontendMetricsServer(frontendPort);
        BackendMetricsServer backendServer = new BackendMetricsServer(backendPort);

        frontendServer.start();
        backendServer.start();

        // Add shutdown hook to gracefully shut down the executor
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.error("Error during executor shutdown", e);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
    }

    private static void loadAnomalyConfig() {
        AnomalyConfig.setInduceHighVisitorRate(Boolean.parseBoolean(System.getenv("INDUCE_HIGH_VISITOR_RATE")));
        AnomalyConfig.setInduceHighErrorRate(Boolean.parseBoolean(System.getenv("INDUCE_HIGH_ERROR_RATE")));
        AnomalyConfig.setInduceHighRequestRateFromSingleIP(Boolean.parseBoolean(System.getenv("INDUCE_HIGH_REQUEST_RATE_FROM_SINGLE_IP")));
        AnomalyConfig.setInduceHighDistinctURLsFromSingleIP(Boolean.parseBoolean(System.getenv("INDUCE_HIGH_DISTINCT_URLS_FROM_SINGLE_IP")));
    }
}

package org.davidgeorgehope;

import org.davidgeorgehope.mysql.MySQLErrorLogGenerator;
import org.davidgeorgehope.mysql.MySQLGeneralLogGenerator;
import org.davidgeorgehope.mysql.MySQLSlowLogGenerator;
import org.davidgeorgehope.nginx.logs.AccessLogGenerator;
import org.davidgeorgehope.nginx.logs.ErrorLogGenerator;
import org.davidgeorgehope.nginx.metrics.BackendMetricsServer;
import org.davidgeorgehope.nginx.metrics.FrontendMetricsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Random;

import java.io.File;
import java.util.concurrent.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class DataGenerator {
    private static final Logger logger = LoggerFactory.getLogger(DataGenerator.class);
    private static final long applicationStartTime = System.currentTimeMillis();

    public static void main(String[] args) {
        // Standard log directories
        String nginxFrontEndLogDir = "/var/log/nginx_frontend";
        String nginxBackendLogDir = "/var/log/nginx_backend";
        String mysqlLogDir = "/var/log/mysql";

        // Create directories if they don't exist (requires appropriate permissions)
        new File(nginxFrontEndLogDir).mkdirs();
        new File(nginxBackendLogDir).mkdirs();
        new File(mysqlLogDir).mkdirs();

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);

        UserSessionManager userSessionManager = new UserSessionManager();

        // Start the random scheduling of anomaly configuration updates
        scheduleAnomalyConfigUpdate(executor);

        // Generate Nginx access logs continuously for frontend and backend
        executor.scheduleAtFixedRate(() -> {
            int logsToGenerate = LogGeneratorUtils.getLogsToGenerate();
            AccessLogGenerator.generateAccessLogs(
                logsToGenerate,
                nginxFrontEndLogDir + "/access.log",
                true,
                userSessionManager
            );
        }, 0, 1, TimeUnit.SECONDS);

        executor.scheduleAtFixedRate(() -> {
            int logsToGenerate = LogGeneratorUtils.getLogsToGenerate();
            AccessLogGenerator.generateAccessLogs(
                logsToGenerate,
                nginxBackendLogDir + "/access.log",
                false,
                userSessionManager
            );
        }, 0, 1, TimeUnit.SECONDS);

        // Generate Nginx error logs less frequently for frontend and backend
        executor.scheduleAtFixedRate(() -> {
            ErrorLogGenerator.generateErrorLogs(
                1,
                nginxFrontEndLogDir + "/error.log",
                true
            );
        }, 0, 5, TimeUnit.SECONDS);

        executor.scheduleAtFixedRate(() -> {
            ErrorLogGenerator.generateErrorLogs(
                1,
                nginxBackendLogDir + "/error.log",
                false
            );
        }, 0, 5, TimeUnit.SECONDS);

        // Generate MySQL logs at fixed rates
        executor.scheduleAtFixedRate(() -> {
            MySQLErrorLogGenerator.generateErrorLogs(
                1,
                mysqlLogDir + "/error.log",
                executor
            );
        }, 0, 10, TimeUnit.SECONDS);

        executor.scheduleAtFixedRate(() -> {
            MySQLSlowLogGenerator.generateSlowLogs(
                1,
                mysqlLogDir + "/mysql-slow.log"
            );
        }, 0, 15, TimeUnit.SECONDS);

        executor.scheduleAtFixedRate(() -> {
            MySQLGeneralLogGenerator.generateGeneralLogs(
                1,
                mysqlLogDir + "/mysql.log"
            );
        }, 0, 5, TimeUnit.SECONDS);

        // Start the metrics servers
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

    private static void scheduleAnomalyConfigUpdate(ScheduledExecutorService executor) {

        // Use exponential distribution for delay
        double meanDelay = 7200; // Mean time between anomalies (e.g., 2 hours)
        double delay = getExponentialRandom(meanDelay);

        executor.schedule(() -> {
            updateAnomalyConfig();

            // Schedule to reset the anomaly after a random duration
            double meanAnomalyDuration = 300; // Mean anomaly duration (e.g., 5 minutes)
            double anomalyDuration = getExponentialRandom(meanAnomalyDuration);

            executor.schedule(() -> {
                resetAnomalyConfig();
            }, (long) anomalyDuration, TimeUnit.SECONDS);

            // Reschedule the next anomaly
            scheduleAnomalyConfigUpdate(executor);
        }, (long) delay, TimeUnit.SECONDS);
    }

    private static void updateAnomalyConfig() {
        Random random = new Random();
        int numberOfAnomalies = random.nextInt(3) + 1; // 1 to 3 anomalies

        // Reset all anomalies first
        resetAnomalyConfig();

        // List of possible anomalies with severity
        List<Consumer<Double>> anomalies = Arrays.asList(
            (severity) -> AnomalyConfig.setInduceHighVisitorRate(true),
            (severity) -> AnomalyConfig.setInduceHighErrorRate(true),
            (severity) -> AnomalyConfig.setInduceHighRequestRateFromSingleIP(true),
            (severity) -> AnomalyConfig.setInduceHighDistinctURLsFromSingleIP(true),
            (severity) -> AnomalyConfig.setInduceLowRequestRate(true)
        );

        // Shuffle and activate anomalies
        Collections.shuffle(anomalies);
        for (int i = 0; i < numberOfAnomalies; i++) {
            double severity = 1.0 + random.nextDouble() * 9.0; // Severity between 1 and 10
            anomalies.get(i).accept(severity);
        }

        // Log the activated anomalies and their severities
        logger.info("Anomalies activated with varying severities.");
    }

    private static double getExponentialRandom(double mean) {
        Random random = new Random();
        return -mean * Math.log(1 - random.nextDouble());
    }

    private static void resetAnomalyConfig() {
        // Reset all anomalies to normal state
        AnomalyConfig.setInduceHighVisitorRate(false);
        AnomalyConfig.setInduceHighErrorRate(false);
        AnomalyConfig.setInduceHighRequestRateFromSingleIP(false);
        AnomalyConfig.setInduceHighDistinctURLsFromSingleIP(false);
        AnomalyConfig.setInduceLowRequestRate(false);
        // AnomalyConfig.setInduceDatabaseOutage(false); // Now handled separately

        // Removed from here
        // MySQLErrorLogGenerator.resetLowStorageWarningCount();

        logger.info("Anomaly configuration reset to normal.");
    }

    public static long getApplicationStartTime() {
        return applicationStartTime;
    }
}

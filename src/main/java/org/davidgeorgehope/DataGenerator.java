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

public class DataGenerator {
    private static final Logger logger = LoggerFactory.getLogger(DataGenerator.class);
    private static final long applicationStartTime = System.currentTimeMillis();

    private static final Random random = new Random();
    private static boolean disableAnomalies = false; // Moved from local variable to static field

    public static void main(String[] args) {
        // Parse command-line arguments

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);

        for (String arg : args) {
            if (arg.equalsIgnoreCase("--no-anomalies")) {
                disableAnomalies = true;
                scheduleAnomalyReenabling(executor);
                logger.info("Anomaly generation and database outages are disabled for 24 hours.");
                break;
            }
        }

        // Standard log directories
        String nginxFrontEndLogDir = "/var/log/nginx_frontend";
        String nginxBackendLogDir = "/var/log/nginx_backend";
        String mysqlLogDir = "/var/log/mysql";

        // Create directories if they don't exist (requires appropriate permissions)
        new File(nginxFrontEndLogDir).mkdirs();
        new File(nginxBackendLogDir).mkdirs();
        new File(mysqlLogDir).mkdirs();


        UserSessionManager userSessionManager = new UserSessionManager();

        // Start the random scheduling of anomaly configuration updates if anomalies are enabled
        if (!disableAnomalies) {
            scheduleAnomalyConfigUpdate(executor);
        } else {
            // Ensure anomalies are reset
            resetAnomalyConfig();
        }

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
                executor,
                disableAnomalies // Now refers to the static field
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
        double maxDelay = 10800; // Maximum delay (e.g., 3 hours)
        double delay = getTruncatedExponentialRandom(meanDelay, maxDelay);

        executor.schedule(() -> {
            updateAnomalyConfig();

            // Schedule to reset the anomaly after a random duration
            double meanAnomalyDuration = 300; // Mean anomaly duration (e.g., 5 minutes)
            double maxAnomalyDuration = 600;  // Maximum anomaly duration (e.g., 10 minutes)
            double anomalyDuration = getTruncatedExponentialRandom(meanAnomalyDuration, maxAnomalyDuration);

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
        List<Runnable> anomalies = Arrays.asList(
            () -> AnomalyConfig.setInduceHighVisitorRate(true),
            () -> AnomalyConfig.setInduceHighErrorRate(true),
            () -> AnomalyConfig.setInduceHighRequestRateFromSingleIP(true),
            () -> AnomalyConfig.setInduceHighDistinctURLsFromSingleIP(true),
            () -> AnomalyConfig.setInduceLowRequestRate(true)        );

        // Shuffle and activate anomalies
        Collections.shuffle(anomalies);
        for (int i = 0; i < numberOfAnomalies; i++) {
            anomalies.get(i).run();
        }

        // Log the activated anomalies
        logger.info("Anomalies activated.");
    }



    private static double getTruncatedExponentialRandom(double mean, double maxDelay) {
        double u = random.nextDouble();
        double Fmax = 1 - Math.exp(-maxDelay / mean);
        double delay = -mean * Math.log(1 - u * Fmax);
        return delay;
    }

    private static void resetAnomalyConfig() {
        // Reset all anomalies to normal state
        AnomalyConfig.setInduceHighVisitorRate(false);
        AnomalyConfig.setInduceHighErrorRate(false);
        AnomalyConfig.setInduceHighRequestRateFromSingleIP(false);
        AnomalyConfig.setInduceHighDistinctURLsFromSingleIP(false);
        AnomalyConfig.setInduceLowRequestRate(false);


        logger.info("Anomaly configuration reset to normal.");
    }

    public static long getApplicationStartTime() {
        return applicationStartTime;
    }

    private static void scheduleAnomalyReenabling(ScheduledExecutorService executor) {
        executor.schedule(() -> {
            disableAnomalies = false;
            logger.info("24-hour period elapsed. Anomalies are now re-enabled.");
            scheduleAnomalyConfigUpdate(executor);
        }, 24, TimeUnit.HOURS);
    }
}

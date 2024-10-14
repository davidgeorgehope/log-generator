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
        Random random = new Random();
    
        int minDelay = 3600; // Minimum delay between anomalies (e.g., 1 hour)
        int maxDelay = 10800; // Maximum delay between anomalies (e.g., 3 hours)
        int delay = random.nextInt(maxDelay - minDelay + 1) + minDelay;
    
        executor.schedule(() -> {
            updateAnomalyConfig();
    
            // Schedule to reset the anomaly after a random short duration
            int minAnomalyDuration = 180; // Minimum anomaly duration (e.g., 3 minutes)
            int maxAnomalyDuration = 400; // Maximum anomaly duration (e.g., 10 minutes)
            int anomalyDuration = random.nextInt(maxAnomalyDuration - minAnomalyDuration + 1) + minAnomalyDuration;
    
            executor.schedule(() -> {
                resetAnomalyConfig();
            }, anomalyDuration, TimeUnit.SECONDS);
    
            // Reschedule the next anomaly
            scheduleAnomalyConfigUpdate(executor);
        }, delay, TimeUnit.SECONDS);
    }

    private static void updateAnomalyConfig() {
        // Randomly set anomalies in a complementary pattern
        Random random = new Random();
        int anomalyType = random.nextInt(5); // Now 5 possible anomalies, excluding database outage

        // Reset all anomalies except database outage (handled separately)
        AnomalyConfig.setInduceHighVisitorRate(false);
        AnomalyConfig.setInduceHighErrorRate(false);
        AnomalyConfig.setInduceHighRequestRateFromSingleIP(false);
        AnomalyConfig.setInduceHighDistinctURLsFromSingleIP(false);
        AnomalyConfig.setInduceLowRequestRate(false);

        switch (anomalyType) {
            case 0:
                AnomalyConfig.setInduceHighVisitorRate(true);
                break;
            case 1:
                AnomalyConfig.setInduceHighErrorRate(true);
                break;
            case 2:
                AnomalyConfig.setInduceHighRequestRateFromSingleIP(true);
                break;
            case 3:
                AnomalyConfig.setInduceHighDistinctURLsFromSingleIP(true);
                break;
            case 4:
                AnomalyConfig.setInduceLowRequestRate(true);
                break;
            // No case for database outage; it will be triggered based on warning count
        }

        logger.info("Anomaly configuration updated: HighVisitorRate={}, HighErrorRate={}, HighRequestRateFromSingleIP={}, HighDistinctURLsFromSingleIP={}, LowRequestRate={}",
                AnomalyConfig.isInduceHighVisitorRate(), AnomalyConfig.isInduceHighErrorRate(),
                AnomalyConfig.isInduceHighRequestRateFromSingleIP(), AnomalyConfig.isInduceHighDistinctURLsFromSingleIP(),
                AnomalyConfig.isInduceLowRequestRate());
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

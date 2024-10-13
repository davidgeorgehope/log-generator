package org.davidgeorgehope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Random;

import java.io.File;
import java.util.concurrent.*;

public class NginxLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(NginxLogGenerator.class);

    public static void main(String[] args) {
        String frontendLogDir = "logs/frontend";
        String backendLogDir = "logs/backend";

        String mysqlLogDir = "logs/mysql";
        new File(mysqlLogDir).mkdirs();


        // Create directories if they don't exist
        new File(frontendLogDir).mkdirs();
        new File(backendLogDir).mkdirs();

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);

        UserSessionManager userSessionManager = new UserSessionManager();

        // Start the random scheduling of anomaly configuration updates
        scheduleAnomalyConfigUpdate(executor);

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


        // Generate MySQL error logs at a fixed rate
        executor.scheduleAtFixedRate(() -> {
            MySQLErrorLogGenerator.generateErrorLogs(1, mysqlLogDir + "/mysql_error.log");
        }, 0, 10, TimeUnit.SECONDS);

        // Generate MySQL slow logs at a fixed rate
        executor.scheduleAtFixedRate(() -> {
            MySQLSlowLogGenerator.generateSlowLogs(1, mysqlLogDir + "/mysql_slow.log");
        }, 0, 15, TimeUnit.SECONDS);

        // Generate MySQL general logs at a fixed rate
        executor.scheduleAtFixedRate(() -> {
            MySQLGeneralLogGenerator.generateGeneralLogs(1, mysqlLogDir + "/mysql_general.log");
        }, 0, 5, TimeUnit.SECONDS);

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

    private static void scheduleAnomalyConfigUpdate(ScheduledExecutorService executor) {
        Random random = new Random();

        int minDelay = 1500; // 25 miuntes
        int maxDelay = 3000; // 50 minutes
        int delay = random.nextInt(maxDelay - minDelay + 1) + minDelay; // Random delay between 3000 and 6000 seconds

        executor.schedule(() -> {
            updateAnomalyConfig();
            scheduleAnomalyConfigUpdate(executor); // Reschedule after execution
        }, delay, TimeUnit.SECONDS);
    }

    private static void updateAnomalyConfig() {
        // Randomly set anomalies in a complementary pattern
        Random random = new Random();
        int anomalyType = random.nextInt(6); // Now 6 possible anomalies

        // Reset all anomalies
        AnomalyConfig.setInduceHighVisitorRate(false);
        AnomalyConfig.setInduceHighErrorRate(false);
        AnomalyConfig.setInduceHighRequestRateFromSingleIP(false);
        AnomalyConfig.setInduceHighDistinctURLsFromSingleIP(false);
        AnomalyConfig.setInduceLowRequestRate(false);
        AnomalyConfig.setInduceDatabaseOutage(false);

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
            case 5:
                AnomalyConfig.setInduceDatabaseOutage(true); // New anomaly case
                break;
        }
        logger.info("Anomaly configuration updated: HighVisitorRate={}, HighErrorRate={}, HighRequestRateFromSingleIP={}, HighDistinctURLsFromSingleIP={}, LowRequestRate={}, DatabaseOutage={}",
                AnomalyConfig.isInduceHighVisitorRate(), AnomalyConfig.isInduceHighErrorRate(),
                AnomalyConfig.isInduceHighRequestRateFromSingleIP(), AnomalyConfig.isInduceHighDistinctURLsFromSingleIP(),
                AnomalyConfig.isInduceLowRequestRate(), AnomalyConfig.isInduceDatabaseOutage());
    }
}
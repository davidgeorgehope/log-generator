package org.davidgeorgehope;

import org.davidgeorgehope.mysql.MySQLErrorLogGenerator;
import org.davidgeorgehope.mysql.MySQLGeneralLogGenerator;
import org.davidgeorgehope.mysql.MySQLSlowLogGenerator;
import org.davidgeorgehope.nginx.logs.AccessLogGenerator;
import org.davidgeorgehope.nginx.logs.ErrorLogGenerator;
import org.davidgeorgehope.nginx.logs.IngressAccessLogGenerator;
import org.davidgeorgehope.nginx.logs.IngressErrorLogGenerator;
import org.davidgeorgehope.nginx.logs.LogSender;
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

    // Configuration parameter with default value
    private static double meanRequestsPerSecond = 1; // Adjust this default value as needed

    // Port configurations for log streaming
    private static int mysqlErrorPort = -1;
    private static int mysqlStdoutPort = -1;
    private static int nginxBackendErrorPort = -1;
    private static int nginxBackendStdoutPort = -1;
    private static int nginxFrontendErrorPort = -1;
    private static int nginxFrontendStdoutPort = -1;
    private static boolean enablePortStreaming = false;
    
    // NGINX Ingress configuration
    private static boolean enableIngressLogs = true; // Changed default to true
    private static int nginxIngressPort = -1;
    private static int nginxIngressErrorPort = -1; // New port for Ingress error logs
    private static String logFormat = "standard"; // Can be "standard" or "ingress"

    public static void main(String[] args) {
        // Parse command-line arguments

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);

        for (String arg : args) {
            if (arg.equalsIgnoreCase("--no-anomalies")) {
                disableAnomalies = true;
                scheduleAnomalyReenabling(executor);
                logger.info("Anomaly generation and database outages are disabled for 24 hours.");
            } else if (arg.startsWith("--mean-requests-per-second=")) {
                meanRequestsPerSecond = Double.parseDouble(arg.split("=")[1]);
                logger.info("Set meanRequestsPerSecond to " + meanRequestsPerSecond);
            } else if (arg.startsWith("--mysql-error-port=")) {
                mysqlErrorPort = Integer.parseInt(arg.split("=")[1]);
                logger.info("MySQL error logs will be sent to port " + mysqlErrorPort);
                enablePortStreaming = true;
            } else if (arg.startsWith("--mysql-stdout-port=")) {
                mysqlStdoutPort = Integer.parseInt(arg.split("=")[1]);
                logger.info("MySQL stdout logs will be sent to port " + mysqlStdoutPort);
                enablePortStreaming = true;
            } else if (arg.startsWith("--nginx-backend-error-port=")) {
                nginxBackendErrorPort = Integer.parseInt(arg.split("=")[1]);
                logger.info("Nginx backend error logs will be sent to port " + nginxBackendErrorPort);
                enablePortStreaming = true;
            } else if (arg.startsWith("--nginx-backend-stdout-port=")) {
                nginxBackendStdoutPort = Integer.parseInt(arg.split("=")[1]);
                logger.info("Nginx backend stdout logs will be sent to port " + nginxBackendStdoutPort);
                enablePortStreaming = true;
            } else if (arg.startsWith("--nginx-frontend-error-port=")) {
                nginxFrontendErrorPort = Integer.parseInt(arg.split("=")[1]);
                logger.info("Nginx frontend error logs will be sent to port " + nginxFrontendErrorPort);
                enablePortStreaming = true;
            } else if (arg.startsWith("--nginx-frontend-stdout-port=")) {
                nginxFrontendStdoutPort = Integer.parseInt(arg.split("=")[1]);
                logger.info("Nginx frontend stdout logs will be sent to port " + nginxFrontendStdoutPort);
                enablePortStreaming = true;
            } else if (arg.startsWith("--nginx-ingress-port=")) {
                nginxIngressPort = Integer.parseInt(arg.split("=")[1]);
                logger.info("Nginx Ingress logs will be sent to port " + nginxIngressPort);
                enablePortStreaming = true;
                enableIngressLogs = true;
            } else if (arg.startsWith("--nginx-ingress-error-port=")) {
                nginxIngressErrorPort = Integer.parseInt(arg.split("=")[1]);
                logger.info("Nginx Ingress error logs will be sent to port " + nginxIngressErrorPort);
                enablePortStreaming = true;
                enableIngressLogs = true;
            } else if (arg.startsWith("--log-format=")) {
                logFormat = arg.split("=")[1];
                if (logFormat.equalsIgnoreCase("ingress")) {
                    enableIngressLogs = true;
                    logger.info("Log format set to NGINX Ingress");
                } else {
                    logFormat = "standard";
                    logger.info("Log format set to standard");
                }
            }
        }

        // Initialize TCP log senders if port streaming is enabled
        if (enablePortStreaming) {
            if (mysqlErrorPort > 0) LogSender.initializePort(mysqlErrorPort);
            if (mysqlStdoutPort > 0) LogSender.initializePort(mysqlStdoutPort);
            if (nginxBackendErrorPort > 0) LogSender.initializePort(nginxBackendErrorPort);
            if (nginxBackendStdoutPort > 0) LogSender.initializePort(nginxBackendStdoutPort);
            if (nginxFrontendErrorPort > 0) LogSender.initializePort(nginxFrontendErrorPort);
            if (nginxFrontendStdoutPort > 0) LogSender.initializePort(nginxFrontendStdoutPort);
            if (nginxIngressPort > 0) LogSender.initializePort(nginxIngressPort);
            if (nginxIngressErrorPort > 0) LogSender.initializePort(nginxIngressErrorPort);
        }

        // Standard log directories
        String nginxFrontEndLogDir = "/var/log/nginx_frontend";
        String nginxBackendLogDir = "/var/log/nginx_backend";
        String mysqlLogDir = "/var/log/mysql";
        String nginxIngressLogDir = "/var/log/nginx_ingress";

        // Create directories if they don't exist (requires appropriate permissions)
        new File(nginxFrontEndLogDir).mkdirs();
        new File(nginxBackendLogDir).mkdirs();
        new File(mysqlLogDir).mkdirs();
        new File(nginxIngressLogDir).mkdirs();

        UserSessionManager userSessionManager = new UserSessionManager();

        // Start the random scheduling of anomaly configuration updates if anomalies are enabled
        if (!disableAnomalies) {
            scheduleAnomalyConfigUpdate(executor);
        } else {
            // Ensure anomalies are reset
            resetAnomalyConfig();
        }

        // Generate both standard NGINX access logs and Ingress logs
        
        // Standard NGINX access logs generation
        executor.scheduleAtFixedRate(() -> {
            int logsToGenerate = LogGeneratorUtils.getLogsToGenerate(meanRequestsPerSecond);
            AccessLogGenerator.generateAccessLogs(
                logsToGenerate,
                nginxFrontEndLogDir + "/access.log",
                true,
                userSessionManager,
                nginxFrontendStdoutPort
            );
        }, 0, 1, TimeUnit.SECONDS);

        executor.scheduleAtFixedRate(() -> {
            int logsToGenerate = LogGeneratorUtils.getLogsToGenerate(meanRequestsPerSecond);
            AccessLogGenerator.generateAccessLogs(
                logsToGenerate,
                nginxBackendLogDir + "/access.log",
                false,
                userSessionManager,
                nginxBackendStdoutPort
            );
        }, 0, 1, TimeUnit.SECONDS);
        
        // Generate NGINX Ingress logs if enabled
        if (enableIngressLogs) {
            executor.scheduleAtFixedRate(() -> {
                int logsToGenerate = LogGeneratorUtils.getLogsToGenerate(meanRequestsPerSecond);
                IngressAccessLogGenerator.generateIngressLogs(
                    logsToGenerate,
                    nginxIngressLogDir + "/ingress-access.log",
                    true,  // Frontend
                    userSessionManager,
                    nginxIngressPort
                );
            }, 0, 1, TimeUnit.SECONDS);
            
            // Generate NGINX Ingress error logs
            executor.scheduleAtFixedRate(() -> {
                IngressErrorLogGenerator.generateIngressErrorLogs(
                    1,
                    nginxIngressLogDir + "/ingress-error.log",
                    true,  // Frontend
                    nginxIngressErrorPort
                );
            }, 0, 5, TimeUnit.SECONDS);
        }

        // Generate Nginx error logs less frequently for frontend and backend
        executor.scheduleAtFixedRate(() -> {
            ErrorLogGenerator.generateErrorLogs(
                1,
                nginxFrontEndLogDir + "/error.log",
                true,
                nginxFrontendErrorPort
            );
        }, 0, 5, TimeUnit.SECONDS);

        executor.scheduleAtFixedRate(() -> {
            ErrorLogGenerator.generateErrorLogs(
                1,
                nginxBackendLogDir + "/error.log",
                false,
                nginxBackendErrorPort
            );
        }, 0, 5, TimeUnit.SECONDS);

        // Generate MySQL logs at fixed rates
        executor.scheduleAtFixedRate(() -> {
            MySQLErrorLogGenerator.generateErrorLogs(
                1,
                mysqlLogDir + "/error.log",
                executor,
                disableAnomalies,
                mysqlErrorPort
            );
        }, 0, 10, TimeUnit.SECONDS);

        executor.scheduleAtFixedRate(() -> {
            MySQLSlowLogGenerator.generateSlowLogs(
                1,
                mysqlLogDir + "/mysql-slow.log",
                mysqlStdoutPort
            );
        }, 0, 15, TimeUnit.SECONDS);

        executor.scheduleAtFixedRate(() -> {
            MySQLGeneralLogGenerator.generateGeneralLogs(
                1,
                mysqlLogDir + "/mysql.log",
                mysqlErrorPort
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
            
            // Shutdown the LogSender
            if (enablePortStreaming) {
                LogSender.shutdown();
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

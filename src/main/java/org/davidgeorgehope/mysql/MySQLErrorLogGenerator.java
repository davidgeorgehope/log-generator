package org.davidgeorgehope.mysql;

import org.davidgeorgehope.AnomalyConfig;
import org.davidgeorgehope.DataGenerator;
import org.davidgeorgehope.nginx.logs.LogSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class MySQLErrorLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MySQLErrorLogGenerator.class);
    public static final AtomicLong warningStartTime = new AtomicLong(System.currentTimeMillis());

    private static int lowStorageWarningCount = 0;
    private static int warningThreshold = ThreadLocalRandom.current().nextInt(8, 15); // Random threshold between 8 and 14

    public static void generateErrorLogs(int logsToGenerate, String filePath, ScheduledExecutorService executor, boolean anomaliesDisabled) {
        generateErrorLogs(logsToGenerate, filePath, executor, anomaliesDisabled, -1);
    }

    public static void generateErrorLogs(int logsToGenerate, String filePath, ScheduledExecutorService executor, boolean anomaliesDisabled, int port) {
        List<MySQLErrorLogEntry> entries = new ArrayList<>();

        for (int i = 0; i < logsToGenerate; i++) {
            List<MySQLErrorLogEntry> entry = MySQLErrorLogEntry.createRandomEntries(anomaliesDisabled);

            MySQLErrorLogEntry firstEntry = entry.get(0);
            if (firstEntry.isLowStorageWarning()) {
                lowStorageWarningCount++;
            }

            entries.addAll(entry);

            // Trigger database outage after random threshold is reached
            if (!anomaliesDisabled && lowStorageWarningCount >= warningThreshold && !AnomalyConfig.isInduceDatabaseOutage()) {
                AnomalyConfig.setInduceDatabaseOutage(true);
                logger.info("Database outage induced due to low storage warnings.");
                scheduleDatabaseOutageReset(executor);
            }
        }

        // If port is specified, send logs to port only, otherwise write to file
        boolean usePortLogging = port > 0;

        try (FileWriter writer = usePortLogging ? null : new FileWriter(filePath, true)) {
            for (MySQLErrorLogEntry entry : entries) {
                String logEntry = entry.toString();
                
                // Send to TCP port if configured, otherwise write to file
                if (usePortLogging) {
                    LogSender.sendLog(port, logEntry);
                } else {
                    writer.write(logEntry);
                }
            }
        } catch (IOException e) {
            if (!usePortLogging) {
                logger.error("Error writing to MySQL error log file: " + filePath, e);
            }
        }
    }

    private static void scheduleDatabaseOutageReset(ScheduledExecutorService executor) {
        int minOutageDuration = 180; // Minimum outage duration (3 minutes)
        int maxOutageDuration = 600; // Maximum outage duration (10 minutes)
        int outageDuration = ThreadLocalRandom.current().nextInt(minOutageDuration, maxOutageDuration + 1);

        executor.schedule(() -> {
            AnomalyConfig.setInduceDatabaseOutage(false);
            logger.info("Database outage resolved.");

            // Reset the low storage warning count
            resetLowStorageWarningCount();

            // Reset the warning start time
            warningStartTime.set(System.currentTimeMillis());

        }, outageDuration, TimeUnit.SECONDS);
    }

    public static void resetLowStorageWarningCount() {
        lowStorageWarningCount = 0;
        warningThreshold = ThreadLocalRandom.current().nextInt(8, 15); // Reset with a new random threshold
    }
}

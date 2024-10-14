package org.davidgeorgehope.mysql;

import org.davidgeorgehope.AnomalyConfig;
import org.davidgeorgehope.DataGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class MySQLErrorLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MySQLErrorLogGenerator.class);

    private static int lowStorageWarningCount = 0;
    private static int warningThreshold = ThreadLocalRandom.current().nextInt(8, 15); // Random threshold between 8 and 14

    public static void generateErrorLogs(int logsToGenerate, String filePath, ScheduledExecutorService executor) {
        List<MySQLErrorLogEntry> entries = new ArrayList<>();
        long elapsedTimeInSeconds = (System.currentTimeMillis() - DataGenerator.getApplicationStartTime()) / 1000;

        for (int i = 0; i < logsToGenerate; i++) {
            MySQLErrorLogEntry entry = MySQLErrorLogEntry.createRandomEntry(elapsedTimeInSeconds);

            if (entry.isLowStorageWarning()) {
                lowStorageWarningCount++;
            }

            entries.add(entry);

            // Trigger database outage after random threshold is reached
            if (lowStorageWarningCount >= warningThreshold && !AnomalyConfig.isInduceDatabaseOutage()) {
                AnomalyConfig.setInduceDatabaseOutage(true);
                logger.info("Database outage induced due to low storage warnings.");

                // Schedule reset of the database outage
                scheduleDatabaseOutageReset(executor);

                // Reset warning count and set a new random threshold for next time
                lowStorageWarningCount = 0;
                warningThreshold = ThreadLocalRandom.current().nextInt(8, 15);
            }
        }

        try (FileWriter writer = new FileWriter(filePath, true)) {
            for (MySQLErrorLogEntry entry : entries) {
                writer.write(entry.toString());
            }
        } catch (IOException e) {
            logger.error("Error writing MySQL error log", e);
        }
    }

    private static void scheduleDatabaseOutageReset(ScheduledExecutorService executor) {
        int minOutageDuration = 180; // Minimum outage duration (e.g., 3 minutes)
        int maxOutageDuration = 600; // Maximum outage duration (e.g., 10 minutes)
        int outageDuration = ThreadLocalRandom.current().nextInt(minOutageDuration, maxOutageDuration + 1);

        executor.schedule(() -> {
            AnomalyConfig.setInduceDatabaseOutage(false);
            logger.info("Database outage resolved.");

            // Reset the low storage warning count here
            resetLowStorageWarningCount();
        }, outageDuration, TimeUnit.SECONDS);
    }

    public static void resetLowStorageWarningCount() {
        lowStorageWarningCount = 0;
        warningThreshold = ThreadLocalRandom.current().nextInt(8, 15); // Reset with a new random threshold
    }
}

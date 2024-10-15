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
import java.util.concurrent.atomic.AtomicLong;

public class MySQLErrorLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MySQLErrorLogGenerator.class);
    public static final AtomicLong warningStartTime = new AtomicLong(System.currentTimeMillis());

    private static int lowStorageWarningCount = 0;
    private static int warningThreshold = ThreadLocalRandom.current().nextInt(8, 15); // Random threshold between 8 and 14

    public static void generateErrorLogs(int logsToGenerate, String filePath, ScheduledExecutorService executor) {
        List<MySQLErrorLogEntry> entries = new ArrayList<>();

        for (int i = 0; i < logsToGenerate; i++) {
            List<MySQLErrorLogEntry> entry = MySQLErrorLogEntry.createRandomEntries();
           
            MySQLErrorLogEntry firstEntry = entry.get(0);
            if (firstEntry.isLowStorageWarning()) {
                lowStorageWarningCount++;
            }

            entries.addAll(entry);

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

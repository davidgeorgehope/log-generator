package org.davidgeorgehope.mysql;

import org.davidgeorgehope.AnomalyConfig;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class MySQLErrorLogEntry {
    private static final DateTimeFormatter ERROR_LOG_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyMMdd HH:mm:ss");

    private String timestamp;
    private String message;
    private boolean isLowStorageWarning;

    // Reference to warningStartTime
    private static AtomicLong warningStartTime = MySQLErrorLogGenerator.warningStartTime;

    public MySQLErrorLogEntry(String timestamp, String message, boolean isLowStorageWarning) {
        this.timestamp = timestamp;
        this.message = message;
        this.isLowStorageWarning = isLowStorageWarning;
    }

    public MySQLErrorLogEntry(String timestamp, String message) {
        this(timestamp, message, false);
    }

    public static MySQLErrorLogEntry createRandomEntry() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (AnomalyConfig.isInduceDatabaseOutage()) {
            // Generate an outage entry during database outage
            return createOutageEntry();
        } else {
            double warningProbability = calculateWarningProbability();

            // Introduce randomness in warning occurrence
            if (random.nextDouble() < warningProbability * random.nextDouble()) {
                // Generate a low storage warning
                return createLowStorageWarningEntry();
            } else {
                // Generate general log entries
                return createGeneralLogEntry();
            }
        }
    }

    private static double calculateWarningProbability() {
        long elapsedTimeInSeconds = (System.currentTimeMillis() - warningStartTime.get()) / 1000;

        // No warnings in the first 2 hours (7200 seconds)
        if (elapsedTimeInSeconds < 7200) {
            return 0.0;
        } else {
            // Increase probability from 0% to 50% over the next 2 hours
            long timeSinceStartOfWarnings = elapsedTimeInSeconds - 7200;
            double maxProbability = 0.5;
            double probability = Math.min(maxProbability, (double) timeSinceStartOfWarnings / 7200 * maxProbability);
            return probability;
        }
    }

    private static MySQLErrorLogEntry createLowStorageWarningEntry() {
        String timestamp = ZonedDateTime.now().format(ERROR_LOG_TIMESTAMP_FORMATTER);
        String[] tables = {"users", "orders", "products", "transactions"};
        String table = tables[ThreadLocalRandom.current().nextInt(tables.length)];
        String[] messages = {
            "[Warning] Disk space for table '" + table + "' is running low",
            "[Warning] Table '" + table + "' is approaching maximum row count",
            "[Warning] Partition for table '" + table + "' is nearly full"
        };
        String message = messages[ThreadLocalRandom.current().nextInt(messages.length)];
        return new MySQLErrorLogEntry(timestamp, message, true);
    }

    private static MySQLErrorLogEntry createGeneralLogEntry() {
        String timestamp = ZonedDateTime.now().format(ERROR_LOG_TIMESTAMP_FORMATTER);
        String[] messages = {
            "[Note] Plugin 'FEDERATED' is disabled.",
            "[Note] InnoDB: The InnoDB memory heap is disabled",
            "[Note] InnoDB: Mutexes and rw_locks use GCC atomic builtins",
            "[Note] InnoDB: Compressed tables use zlib 1.2.3",
            "[Note] InnoDB: Using Linux native AIO",
            "[Note] IPv6 is available.",
            "[Note] Event Scheduler: Loaded 0 events",
            "[Note] /usr/sbin/mysqld: ready for connections."
        };
        String message = messages[ThreadLocalRandom.current().nextInt(messages.length)];
        return new MySQLErrorLogEntry(timestamp, message);
    }

    public static MySQLErrorLogEntry createOutageEntry() {
        String timestamp = ZonedDateTime.now().format(ERROR_LOG_TIMESTAMP_FORMATTER);
        String errorLevel = "ERROR";
        int errorCode = 1114; // Error code for ER_RECORD_FILE_FULL
        String sqlState = "HY000";
        String errorMessage = "The table 'orders' is full";

        // Include log level in square brackets
        String fullMessage = "[" + errorLevel + "] " + errorCode + " (" + sqlState + "): " + errorMessage;
        return new MySQLErrorLogEntry(timestamp, fullMessage);
    }

    public boolean isLowStorageWarning() {
        return isLowStorageWarning;
    }

    @Override
    public String toString() {
        return timestamp + " " + message + System.lineSeparator();
    }
}

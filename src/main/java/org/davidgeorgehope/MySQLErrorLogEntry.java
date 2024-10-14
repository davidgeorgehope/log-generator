package org.davidgeorgehope;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class MySQLErrorLogEntry {
    private static final DateTimeFormatter ERROR_LOG_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyMMdd HH:mm:ss");

    private String timestamp;
    private String message;

    public MySQLErrorLogEntry(String timestamp, String message) {
        this.timestamp = timestamp;
        this.message = message;
    }

    public static MySQLErrorLogEntry createRandomEntry() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int entryType = random.nextInt(100);

        if (entryType < 20) {
            // 20% chance to generate a low storage warning
            return createLowStorageWarningEntry();
        } else {
            // Other general log entries
            return createGeneralLogEntry();
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
        return new MySQLErrorLogEntry(timestamp, message);
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

    @Override
    public String toString() {
        return timestamp + " " + message + System.lineSeparator();
    }

    public static MySQLErrorLogEntry createOutageEntry() {
        String timestamp = ZonedDateTime.now().format(ERROR_LOG_TIMESTAMP_FORMATTER);
        String errorLevel = "ERROR";
        int errorCode = 1114; // Error code for ER_RECORD_FILE_FULL
        String sqlState = "HY000";
        String errorMessage = "The table 'orders' is full";

        String fullMessage = errorLevel + " " + errorCode + " (" + sqlState + "): " + errorMessage;
        return new MySQLErrorLogEntry(timestamp, fullMessage);
    }
}

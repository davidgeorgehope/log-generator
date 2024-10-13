package org.davidgeorgehope;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class MySQLErrorLogEntry {
    private static final DateTimeFormatter ERROR_LOG_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyMMdd HH:mm:ss");

    private final String timestamp;
    private final String errorLevel;
    private final int errorCode;
    private final String errorMessage;

    private MySQLErrorLogEntry(String timestamp, String errorLevel, int errorCode, String errorMessage) {
        this.timestamp = timestamp;
        this.errorLevel = errorLevel;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static MySQLErrorLogEntry createRandomEntry() {
        String timestamp = ZonedDateTime.now().format(ERROR_LOG_TIMESTAMP_FORMATTER);
        String errorLevel = getRandomErrorLevel();
        int errorCode = getRandomErrorCode();
        String errorMessage = LogGeneratorUtils.getRandomMySQLErrorMessage(errorCode);

        return new MySQLErrorLogEntry(timestamp, errorLevel, errorCode, errorMessage);
    }

    private static String getRandomErrorLevel() {
        String[] levels = {"ERROR", "WARNING", "NOTE"};
        return LogGeneratorUtils.getRandomElement(levels);
    }

    private static int getRandomErrorCode() {
        Integer[] errorCodes = {1045, 1146, 2003, 1064, 1054};
        return LogGeneratorUtils.getRandomElement(errorCodes);
    }

    @Override
    public String toString() {
        return String.format("%s [%s] [Code: %d] %s%n", timestamp, errorLevel, errorCode, errorMessage);
    }

    public static MySQLErrorLogEntry createOutageEntry() {
        String timestamp = ZonedDateTime.now().format(ERROR_LOG_TIMESTAMP_FORMATTER);
        String errorLevel = "ERROR";
        int errorCode = 1114; // Error code for ER_RECORD_FILE_FULL
        String errorMessage = "The table 'orders' is full";

        return new MySQLErrorLogEntry(timestamp, errorLevel, errorCode, errorMessage);
    }
}

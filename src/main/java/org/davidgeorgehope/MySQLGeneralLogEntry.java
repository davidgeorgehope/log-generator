package org.davidgeorgehope;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class MySQLGeneralLogEntry {
    private static final DateTimeFormatter GENERAL_LOG_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    private final String timestamp;
    private final String threadId;
    private final String commandType;
    private final String argument;

    private MySQLGeneralLogEntry(String timestamp, String threadId, String commandType, String argument) {
        this.timestamp = timestamp;
        this.threadId = threadId;
        this.commandType = commandType;
        this.argument = argument;
    }

    public static MySQLGeneralLogEntry createRandomEntry() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String timestamp = ZonedDateTime.now().format(GENERAL_LOG_TIMESTAMP_FORMATTER);
        String threadId = String.valueOf(random.nextInt(1000, 5000));
        String commandType = getRandomCommandType();
        String argument = getArgumentForCommandType(commandType);

        return new MySQLGeneralLogEntry(timestamp, threadId, commandType, argument);
    }

    private static String getRandomCommandType() {
        String[] commandTypes = {"Connect", "Query", "Quit"};
        return LogGeneratorUtils.getRandomElement(commandTypes);
    }

    private static String getArgumentForCommandType(String commandType) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        switch (commandType) {
            case "Connect":
                String user = LogGeneratorUtils.getRandomElement(new String[]{"root", "db_user", "app_user"});
                String host = LogGeneratorUtils.generateRandomIP(false);
                return String.format("User@Host: %s[%s] @ %s []", user, user, host);
            case "Query":
                return LogGeneratorUtils.getRandomSlowQuerySQL();
            case "Quit":
                return "";
            default:
                return "";
        }
    }

    @Override
    public String toString() {
        return String.format("%s\t%s\t%s\t%s%n", timestamp, threadId, commandType, argument);
    }
}

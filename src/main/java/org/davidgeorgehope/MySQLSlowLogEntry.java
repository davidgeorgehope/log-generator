package org.davidgeorgehope;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class MySQLSlowLogEntry {
    private static final DateTimeFormatter SLOW_LOG_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String startTime;
    private final double queryTime;
    private final double lockTime;
    private final int rowsSent;
    private final int rowsExamined;
    private final String sql;

    private MySQLSlowLogEntry(String startTime, double queryTime, double lockTime,
                              int rowsSent, int rowsExamined, String sql) {
        this.startTime = startTime;
        this.queryTime = queryTime;
        this.lockTime = lockTime;
        this.rowsSent = rowsSent;
        this.rowsExamined = rowsExamined;
        this.sql = sql;
    }

    public static MySQLSlowLogEntry createRandomEntry() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String startTime = ZonedDateTime.now().format(SLOW_LOG_TIMESTAMP_FORMATTER);
        double queryTime = random.nextDouble(0.5, 5.0); // Between 0.5 and 5 seconds
        double lockTime = random.nextDouble(0.0, 0.5);  // Between 0 and 0.5 seconds
        int rowsSent = random.nextInt(1, 1000);
        int rowsExamined = rowsSent + random.nextInt(1, 10000);
        String sql = LogGeneratorUtils.getRandomSlowQuerySQL();

        return new MySQLSlowLogEntry(startTime, queryTime, lockTime, rowsSent, rowsExamined, sql);
    }

    @Override
    public String toString() {
        return String.format("# Time: %s%n" +
                "# Query_time: %.2f  Lock_time: %.2f Rows_sent: %d  Rows_examined: %d%n" +
                "%s;%n", startTime, queryTime, lockTime, rowsSent, rowsExamined, sql);
    }
}

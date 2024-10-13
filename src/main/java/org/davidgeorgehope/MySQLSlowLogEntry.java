package org.davidgeorgehope;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class MySQLSlowLogEntry {
    private static final DateTimeFormatter SLOW_LOG_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String timestamp;
    private String user;
    private String host;
    private String ip;
    private long threadId;
    private String schema;
    private String qcHit;
    private double queryTime;
    private double lockTime;
    private long rowsSent;
    private long rowsExamined;
    private long timestampUnix;
    private String query;

    public MySQLSlowLogEntry(String timestamp, String user, String host, String ip,
                             long threadId, String schema, String qcHit,
                             double queryTime, double lockTime, long rowsSent,
                             long rowsExamined, long timestampUnix, String query) {
        this.timestamp = timestamp;
        this.user = user;
        this.host = host;
        this.ip = ip;
        this.threadId = threadId;
        this.schema = schema;
        this.qcHit = qcHit;
        this.queryTime = queryTime;
        this.lockTime = lockTime;
        this.rowsSent = rowsSent;
        this.rowsExamined = rowsExamined;
        this.timestampUnix = timestampUnix;
        this.query = query;
    }

    public static MySQLSlowLogEntry createRandomEntry() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String timestamp = ZonedDateTime.now().format(SLOW_LOG_TIMESTAMP_FORMATTER);
        String user = LogGeneratorUtils.getRandomElement(new String[]{"app_user", "db_user", "readonly_user"});
        String host = "localhost";
        String ip = LogGeneratorUtils.generateRandomIP(false);
        long threadId = random.nextLong(1000, 10000);
        String schema = LogGeneratorUtils.getRandomElement(new String[]{"orders_db", "users_db", "products_db"});
        String qcHit = random.nextBoolean() ? "Yes" : "No";
        double queryTime = LogGeneratorUtils.round(random.nextDouble(0.5, 5.0), 2);
        double lockTime = LogGeneratorUtils.round(random.nextDouble(0.0, 0.5), 2);
        long rowsSent = random.nextInt(100, 1000);
        long rowsExamined = random.nextInt(1000, 10000);
        long timestampUnix = System.currentTimeMillis() / 1000L;
        String query = generateRandomQuery();

        return new MySQLSlowLogEntry(timestamp, user, host, ip, threadId, schema, qcHit,
                queryTime, lockTime, rowsSent, rowsExamined, timestampUnix, query);
    }

    private static String generateRandomQuery() {
        String[] queries = {
            "SELECT * FROM orders WHERE customer_id = " + ThreadLocalRandom.current().nextInt(1000, 10000) + ";",
            "UPDATE products SET stock = stock - 1 WHERE product_id = " + ThreadLocalRandom.current().nextInt(1000, 10000) + ";",
            "INSERT INTO user_sessions (session_id, user_id) VALUES ('" + LogGeneratorUtils.generateRandomSessionId() + "', " + ThreadLocalRandom.current().nextInt(1000, 10000) + ");",
            "DELETE FROM carts WHERE created_at < NOW() - INTERVAL 30 DAY;"
        };
        return LogGeneratorUtils.getRandomElement(queries);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Time: ").append(timestamp).append(System.lineSeparator());
        sb.append("# User@Host: ").append(user).append("[").append(user).append("] @ ").append(host)
            .append(" [").append(ip).append("]").append(System.lineSeparator());
        sb.append("# Thread_id: ").append(threadId).append("  Schema: ").append(schema).append("  QC_hit: ").append(qcHit)
            .append(System.lineSeparator());
        sb.append("# Query_time: ").append(queryTime).append("  Lock_time: ").append(lockTime)
            .append(" Rows_sent: ").append(rowsSent).append("  Rows_examined: ").append(rowsExamined)
            .append(System.lineSeparator());
        sb.append("SET timestamp=").append(timestampUnix).append(";").append(System.lineSeparator());
        sb.append(query).append(System.lineSeparator());
        return sb.toString();
    }
}

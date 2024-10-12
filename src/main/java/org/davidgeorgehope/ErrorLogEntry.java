package org.davidgeorgehope;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class ErrorLogEntry extends LogEntry {
    private static final DateTimeFormatter ERROR_LOG_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private static final String ERROR_LOG_TEMPLATE = "%s [%s] %d#%d: *%d %s, client: %s, server: %s, request: \"%s\", host: \"%s\"%n";

    private final String date;
    private final String level;
    private final int pid;
    private final int tid;
    private final int connection;
    private final String message;
    private final String clientIP;
    private final String server;
    private final String request;
    private final String host;

    private ErrorLogEntry(String date, String level, int pid, int tid, int connection, String message,
                          String clientIP, String server, String request, String host) {
        this.date = date;
        this.level = level;
        this.pid = pid;
        this.tid = tid;
        this.connection = connection;
        this.message = message;
        this.clientIP = clientIP;
        this.server = server;
        this.request = request;
        this.host = host;
    }

    public static ErrorLogEntry createRandomEntry(boolean isFrontend) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String date = ZonedDateTime.now().format(ERROR_LOG_TIMESTAMP_FORMATTER);
        String level = "error";
        int pid = random.nextInt(10000) + 1000;
        int tid = random.nextInt(10);
        int connection = random.nextInt(10000);
        String clientIP = LogGeneratorUtils.generateRandomIP(false);
        String server = isFrontend ? "frontend.example.com" : "api.example.com";
        HttpMethod method = LogGeneratorUtils.getRandomHttpMethod();
        String url = LogGeneratorUtils.getRandomURL("-", isFrontend);
        String protocol = "HTTP/1.1";
        String request = method + " " + url + " " + protocol;
        String host = server;
        String message = LogGeneratorUtils.getRandomErrorMessage(isFrontend, url);

        return new ErrorLogEntry(date, level, pid, tid, connection, message,
                clientIP, server, request, host);
    }

    @Override
    public String toString() {
        return String.format(ERROR_LOG_TEMPLATE, date, level, pid, tid,
                connection, message, clientIP, server, request, host);
    }
}
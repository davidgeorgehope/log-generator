package org.davidgeorgehope;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class AccessLogEntry extends LogEntry {
    private static final DateTimeFormatter ACCESS_LOG_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

    private static final String ACCESS_LOG_TEMPLATE = "%s - %s [%s] \"%s\" %d %d \"%s\" \"%s\" %.3f \"%s\" %s%n";

    private final String ip;
    private final String username;
    private final String timestamp;
    private final String request;
    private final int status;
    private final int size;
    private final String referrer;
    private final String userAgent;
    private final double responseTime;
    private final String countryCode;
    private final String headers;

    private AccessLogEntry(String ip, String username, String timestamp, String request, int status,
                           int size, String referrer, String userAgent, double responseTime,
                           String countryCode, String headers) {
        this.ip = ip;
        this.username = username;
        this.timestamp = timestamp;
        this.request = request;
        this.status = status;
        this.size = size;
        this.referrer = referrer;
        this.userAgent = userAgent;
        this.responseTime = responseTime;
        this.countryCode = countryCode;
        this.headers = headers;
    }

    public static AccessLogEntry createRandomEntry(boolean isFrontend, UserSessionManager userSessionManager) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String ip = LogGeneratorUtils.generateRandomIP(false);
        String username = "-";
        if (random.nextDouble() < 0.7) { // 70% chance of being a logged-in user
            username = userSessionManager.getOrCreateActiveUser(ip);
        }
        String countryCode = LogGeneratorUtils.getCountryCode(ip);
        String timestamp = ZonedDateTime.now().format(ACCESS_LOG_TIMESTAMP_FORMATTER);
        HttpMethod method = LogGeneratorUtils.getRandomHttpMethod();
        String url = LogGeneratorUtils.getRandomURL(username, isFrontend);
        String protocol = "HTTP/1.1";
        String request = method + " " + url + " " + protocol;
        int status = LogGeneratorUtils.getStatusCode(ip, false);
        int size = random.nextInt(5000) + 200;
        String referrer = "-";  // No referrer
        String userAgent = LogGeneratorUtils.getRandomUserAgent();
        double responseTime = LogGeneratorUtils.generateResponseTime();
        List<String> headersList = LogGeneratorUtils.generateHeadersList(username);
        String headers = headersList.stream()
                .map(h -> "\"" + h + "\"")
                .collect(Collectors.joining(" "));

        return new AccessLogEntry(ip, username, timestamp, request, status, size,
                referrer, userAgent, responseTime, countryCode, headers);
    }

    public static AccessLogEntry createErrorEntry(boolean isFrontend, UserSessionManager userSessionManager) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String ip = LogGeneratorUtils.generateRandomIP(false);
        String username = "-"; // Assuming no user is logged in during an error
        String countryCode = LogGeneratorUtils.getCountryCode(ip);
        String timestamp = ZonedDateTime.now().format(ACCESS_LOG_TIMESTAMP_FORMATTER);
        HttpMethod method = LogGeneratorUtils.getRandomHttpMethod();
        String url = LogGeneratorUtils.getRandomURL(username, isFrontend); // Use existing method
        String protocol = "HTTP/1.1";
        String request = method + " " + url + " " + protocol;
        int status = 500; // Internal Server Error
        int size = random.nextInt(500) + 100;
        String referrer = "-"; // No referrer
        String userAgent = LogGeneratorUtils.getRandomUserAgent();
        double responseTime = LogGeneratorUtils.generateResponseTime();
        List<String> headersList = LogGeneratorUtils.generateHeadersList(username);
        String headers = headersList.stream()
                .map(h -> "\"" + h + "\"")
                .collect(Collectors.joining(" "));

        return new AccessLogEntry(ip, username, timestamp, request, status, size,
                referrer, userAgent, responseTime, countryCode, headers);
    }

    @Override
    public String toString() {
        return String.format(ACCESS_LOG_TEMPLATE, ip, username, timestamp, request,
                status, size, referrer, userAgent, responseTime, countryCode, headers);
    }
}

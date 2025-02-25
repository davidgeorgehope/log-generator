package org.davidgeorgehope.nginx.logs;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.davidgeorgehope.HttpMethod;
import org.davidgeorgehope.LogEntry;
import org.davidgeorgehope.LogGeneratorUtils;
import org.davidgeorgehope.UserSessionManager;

public class IngressAccessLogEntry extends LogEntry {
    private static final DateTimeFormatter ACCESS_LOG_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

    private static final String INGRESS_LOG_TEMPLATE = "%s - %s [%s] \"%s\" %d %d \"%s\" \"%s\" %d %.3f %s %d %.3f %d %s%n";

    private final String ip;
    private final String username;
    private final String timestamp;
    private final String request;
    private final int status;
    private final int bodyBytesSent;
    private final String referrer;
    private final String userAgent;
    private final int requestLength;
    private final double requestTime;
    private final String upstreamAddr;
    private final int upstreamResponseLength;
    private final double upstreamResponseTime;
    private final int upstreamStatus;
    private final String host;

    private IngressAccessLogEntry(String ip, String username, String timestamp, String request, int status,
                         int bodyBytesSent, String referrer, String userAgent, int requestLength,
                         double requestTime, String upstreamAddr, int upstreamResponseLength,
                         double upstreamResponseTime, int upstreamStatus, String host) {
        this.ip = ip;
        this.username = username;
        this.timestamp = timestamp;
        this.request = request;
        this.status = status;
        this.bodyBytesSent = bodyBytesSent;
        this.referrer = referrer;
        this.userAgent = userAgent;
        this.requestLength = requestLength;
        this.requestTime = requestTime;
        this.upstreamAddr = upstreamAddr;
        this.upstreamResponseLength = upstreamResponseLength;
        this.upstreamResponseTime = upstreamResponseTime;
        this.upstreamStatus = upstreamStatus;
        this.host = host;
    }

    public static IngressAccessLogEntry createRandomEntry(boolean isFrontend, UserSessionManager userSessionManager) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String ip = LogGeneratorUtils.generateRandomIP(false);
        String username = "-";
        if (random.nextDouble() < 0.7) { // 70% chance of being a logged-in user
            username = userSessionManager.getOrCreateActiveUser(ip);
        }
        
        String timestamp = ZonedDateTime.now().format(ACCESS_LOG_TIMESTAMP_FORMATTER);
        HttpMethod method = LogGeneratorUtils.getRandomHttpMethod();
        String url = LogGeneratorUtils.getRandomURL(username, isFrontend);
        String protocol = "HTTP/1.1";
        String request = method + " " + url + " " + protocol;
        int status = LogGeneratorUtils.getStatusCode(ip, false);
        int bodyBytesSent = random.nextInt(5000) + 200;
        String referrer = "-";  // No referrer
        String userAgent = LogGeneratorUtils.getRandomUserAgent();
        
        // Ingress specific fields
        int requestLength = random.nextInt(1000) + 100;
        double requestTime = LogGeneratorUtils.generateResponseTime();
        String upstreamAddr = generateRandomUpstreamAddr();
        int upstreamResponseLength = bodyBytesSent - random.nextInt(50);  // Slightly smaller than body bytes sent
        double upstreamResponseTime = requestTime * 0.8;  // Usually less than request time
        int upstreamStatus = status;  // Usually the same as status
        String host = generateRandomHost(isFrontend);

        return new IngressAccessLogEntry(ip, username, timestamp, request, status, bodyBytesSent,
                referrer, userAgent, requestLength, requestTime, upstreamAddr, upstreamResponseLength,
                upstreamResponseTime, upstreamStatus, host);
    }

    public static IngressAccessLogEntry createErrorEntry(boolean isFrontend, UserSessionManager userSessionManager) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String ip = LogGeneratorUtils.generateRandomIP(false);
        String username = "-"; // Assuming no user is logged in during an error
        String timestamp = ZonedDateTime.now().format(ACCESS_LOG_TIMESTAMP_FORMATTER);
        HttpMethod method = LogGeneratorUtils.getRandomHttpMethod();
        String url = LogGeneratorUtils.getRandomURL(username, isFrontend);
        String protocol = "HTTP/1.1";
        String request = method + " " + url + " " + protocol;
        int status = 500; // Internal Server Error
        int bodyBytesSent = random.nextInt(500) + 100;
        String referrer = "-"; // No referrer
        String userAgent = LogGeneratorUtils.getRandomUserAgent();
        
        // Ingress specific fields
        int requestLength = random.nextInt(1000) + 100;
        double requestTime = LogGeneratorUtils.generateResponseTime() * 2.0;  // Errors tend to take longer
        String upstreamAddr = generateRandomUpstreamAddr();
        int upstreamResponseLength = random.nextInt(500);
        double upstreamResponseTime = random.nextDouble() * 2.0 + 0.5;  // Longer response time for errors
        int upstreamStatus = 500;  // Error status from upstream
        String host = generateRandomHost(isFrontend);

        return new IngressAccessLogEntry(ip, username, timestamp, request, status, bodyBytesSent,
                referrer, userAgent, requestLength, requestTime, upstreamAddr, upstreamResponseLength,
                upstreamResponseTime, upstreamStatus, host);
    }

    private static String generateRandomUpstreamAddr() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String[] upstreamAddrs = {
            "10.244.0.17:8080", 
            "10.244.0.18:8080", 
            "10.244.0.19:8080",
            "10.244.1.20:8080", 
            "10.244.1.21:8080", 
            "10.244.2.22:8080"
        };
        return upstreamAddrs[random.nextInt(upstreamAddrs.length)];
    }

    private static String generateRandomHost(boolean isFrontend) {
        if (isFrontend) {
            return "example.com";
        } else {
            return "api.example.com";
        }
    }

    @Override
    public String toString() {
        return String.format(INGRESS_LOG_TEMPLATE, ip, username, timestamp, request,
                status, bodyBytesSent, referrer, userAgent, requestLength, requestTime,
                upstreamAddr, upstreamResponseLength, upstreamResponseTime, upstreamStatus, host);
    }
} 
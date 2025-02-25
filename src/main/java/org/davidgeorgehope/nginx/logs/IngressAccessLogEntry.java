package org.davidgeorgehope.nginx.logs;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.davidgeorgehope.HttpMethod;
import org.davidgeorgehope.LogEntry;
import org.davidgeorgehope.LogGeneratorUtils;
import org.davidgeorgehope.UserSessionManager;

public class IngressAccessLogEntry extends LogEntry {
    private static final DateTimeFormatter ACCESS_LOG_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

    // The format matches the expected pattern in the Elastic ingest pipeline for Nginx Ingress Controller logs
    private static final String INGRESS_LOG_TEMPLATE = 
        "%s - %s [%s] \"%s %s HTTP/%s\" %d %d \"%s\" \"%s\" %d %.3f [%s] [%s] %s %s %s %s %s";

    private final String sourceAddress; // source.address
    private final String userName; // user.name
    private final String timestamp; // nginx_ingress_controller.access.time
    private final String httpMethod; // http.request.method
    private final String urlOriginal; // url.original
    private final String httpVersion; // http.version
    private final int statusCode; // http.response.status_code
    private final int bodyBytesSent; // http.response.body.bytes
    private final String httpRequestReferrer; // http.request.referrer
    private final String userAgentOriginal; // user_agent.original
    private final int requestLength; // nginx_ingress_controller.access.http.request.length
    private final double requestTime; // nginx_ingress_controller.access.http.request.time
    private final String upstreamName; // nginx_ingress_controller.access.upstream.name
    private final String upstreamAlternativeName; // nginx_ingress_controller.access.upstream.alternative_name
    private final String upstreamAddressList; // nginx_ingress_controller.access.upstream_address_list
    private final String upstreamResponseLengthList; // nginx_ingress_controller.access.upstream.response.length_list
    private final String upstreamResponseTimeList; // nginx_ingress_controller.access.upstream.response.time_list
    private final String upstreamResponseStatusCodeList; // nginx_ingress_controller.access.upstream.response.status_code_list
    private final String requestId; // nginx_ingress_controller.access.http.request.id

    private IngressAccessLogEntry(
            String sourceAddress,
            String userName,
            String timestamp,
            String httpMethod,
            String urlOriginal,
            String httpVersion,
            int statusCode,
            int bodyBytesSent,
            String httpRequestReferrer,
            String userAgentOriginal,
            int requestLength,
            double requestTime,
            String upstreamName,
            String upstreamAlternativeName,
            String upstreamAddressList,
            String upstreamResponseLengthList,
            String upstreamResponseTimeList,
            String upstreamResponseStatusCodeList,
            String requestId) {
        this.sourceAddress = sourceAddress;
        this.userName = userName;
        this.timestamp = timestamp;
        this.httpMethod = httpMethod;
        this.urlOriginal = urlOriginal;
        this.httpVersion = httpVersion;
        this.statusCode = statusCode;
        this.bodyBytesSent = bodyBytesSent;
        this.httpRequestReferrer = httpRequestReferrer;
        this.userAgentOriginal = userAgentOriginal;
        this.requestLength = requestLength;
        this.requestTime = requestTime;
        this.upstreamName = upstreamName;
        this.upstreamAlternativeName = upstreamAlternativeName;
        this.upstreamAddressList = upstreamAddressList;
        this.upstreamResponseLengthList = upstreamResponseLengthList;
        this.upstreamResponseTimeList = upstreamResponseTimeList;
        this.upstreamResponseStatusCodeList = upstreamResponseStatusCodeList;
        this.requestId = requestId;
    }

    public static IngressAccessLogEntry createRandomEntry(boolean isFrontend, UserSessionManager userSessionManager) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String sourceAddress = LogGeneratorUtils.generateRandomIP(false);
        String userName = "-";
        if (random.nextDouble() < 0.7) { // 70% chance of being a logged-in user
            userName = userSessionManager.getOrCreateActiveUser(sourceAddress);
        }
        
        String timestamp = ZonedDateTime.now().format(ACCESS_LOG_TIMESTAMP_FORMATTER);
        HttpMethod method = LogGeneratorUtils.getRandomHttpMethod();
        String httpMethod = method.toString();
        String urlOriginal = LogGeneratorUtils.getRandomURL(userName, isFrontend);
        String httpVersion = "1.1";
        int statusCode = LogGeneratorUtils.getStatusCode(sourceAddress, false);
        int bodyBytesSent = random.nextInt(5000) + 200;
        String httpRequestReferrer = "-";  // No referrer
        String userAgentOriginal = LogGeneratorUtils.getRandomUserAgent();
        
        // Ingress specific fields
        int requestLength = random.nextInt(1000) + 100;
        double requestTime = LogGeneratorUtils.generateResponseTime();
        
        // Upstream info
        String upstreamName = generateRandomUpstreamName(isFrontend);
        String upstreamAlternativeName = generateRandomUpstreamAlternativeName(isFrontend);
        String upstreamAddressList = generateRandomUpstreamAddr();
        
        // Generate response data
        int upstreamResponseLength = bodyBytesSent - random.nextInt(50);  // Slightly smaller than body bytes sent
        double upstreamResponseTime = requestTime * 0.8;  // Usually less than request time
        int upstreamResponseStatusCode = statusCode;  // Usually the same as status
        
        String upstreamResponseLengthList = String.valueOf(upstreamResponseLength);
        String upstreamResponseTimeList = String.format("%.3f", upstreamResponseTime);
        String upstreamResponseStatusCodeList = String.valueOf(upstreamResponseStatusCode);
        
        // Request ID
        String requestId = UUID.randomUUID().toString();

        return new IngressAccessLogEntry(
                sourceAddress,
                userName,
                timestamp,
                httpMethod,
                urlOriginal,
                httpVersion,
                statusCode,
                bodyBytesSent,
                httpRequestReferrer,
                userAgentOriginal,
                requestLength,
                requestTime,
                upstreamName,
                upstreamAlternativeName,
                upstreamAddressList,
                upstreamResponseLengthList,
                upstreamResponseTimeList,
                upstreamResponseStatusCodeList,
                requestId);
    }

    public static IngressAccessLogEntry createErrorEntry(boolean isFrontend, UserSessionManager userSessionManager) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String sourceAddress = LogGeneratorUtils.generateRandomIP(false);
        String userName = "-"; // Assuming no user is logged in during an error
        String timestamp = ZonedDateTime.now().format(ACCESS_LOG_TIMESTAMP_FORMATTER);
        HttpMethod method = LogGeneratorUtils.getRandomHttpMethod();
        String httpMethod = method.toString();
        String urlOriginal = LogGeneratorUtils.getRandomURL(userName, isFrontend);
        String httpVersion = "1.1";
        int statusCode = 500; // Internal Server Error
        int bodyBytesSent = random.nextInt(500) + 100;
        String httpRequestReferrer = "-"; // No referrer
        String userAgentOriginal = LogGeneratorUtils.getRandomUserAgent();
        
        // Ingress specific fields
        int requestLength = random.nextInt(1000) + 100;
        double requestTime = LogGeneratorUtils.generateResponseTime() * 2.0;  // Errors tend to take longer
        
        // Upstream info
        String upstreamName = generateRandomUpstreamName(isFrontend);
        String upstreamAlternativeName = generateRandomUpstreamAlternativeName(isFrontend);
        String upstreamAddressList = generateRandomUpstreamAddr();
        
        // Generate response data
        int upstreamResponseLength = random.nextInt(500);
        double upstreamResponseTime = random.nextDouble() * 2.0 + 0.5;  // Longer response time for errors
        int upstreamResponseStatusCode = 500;  // Error status from upstream
        
        String upstreamResponseLengthList = String.valueOf(upstreamResponseLength);
        String upstreamResponseTimeList = String.format("%.3f", upstreamResponseTime);
        String upstreamResponseStatusCodeList = String.valueOf(upstreamResponseStatusCode);
        
        // Request ID
        String requestId = UUID.randomUUID().toString();

        return new IngressAccessLogEntry(
                sourceAddress,
                userName,
                timestamp,
                httpMethod,
                urlOriginal,
                httpVersion,
                statusCode,
                bodyBytesSent,
                httpRequestReferrer,
                userAgentOriginal,
                requestLength,
                requestTime,
                upstreamName,
                upstreamAlternativeName,
                upstreamAddressList,
                upstreamResponseLengthList,
                upstreamResponseTimeList,
                upstreamResponseStatusCodeList,
                requestId);
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
    
    private static String generateRandomUpstreamName(boolean isFrontend) {
        if (isFrontend) {
            return "frontend-svc";
        } else {
            return "backend-svc";
        }
    }
    
    private static String generateRandomUpstreamAlternativeName(boolean isFrontend) {
        if (isFrontend) {
            return "default-frontend-svc-80";
        } else {
            return "default-backend-svc-80";
        }
    }

    @Override
    public String toString() {
        return String.format(INGRESS_LOG_TEMPLATE,
                sourceAddress,
                userName,
                timestamp,
                httpMethod,
                urlOriginal,
                httpVersion,
                statusCode,
                bodyBytesSent,
                httpRequestReferrer,
                userAgentOriginal,
                requestLength,
                requestTime,
                upstreamName,
                upstreamAlternativeName,
                upstreamAddressList,
                upstreamResponseLengthList,
                upstreamResponseTimeList,
                upstreamResponseStatusCodeList,
                requestId);
    }
} 
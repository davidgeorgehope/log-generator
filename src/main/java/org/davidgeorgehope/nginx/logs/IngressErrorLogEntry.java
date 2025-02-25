package org.davidgeorgehope.nginx.logs;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.davidgeorgehope.LogEntry;

public class IngressErrorLogEntry extends LogEntry {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final String[] ERROR_LEVELS = {"info", "notice", "warn", "error", "crit", "alert", "emerg"};
    private static final String[] ERROR_MESSAGES = {
        "upstream timed out (110: Connection timed out) while connecting to upstream",
        "SSL certificate verification error: certificate has expired",
        "client intended to send too large chunked body: %d bytes",
        "upstream prematurely closed connection while reading response header from upstream",
        "open socket #%d left in connection %d",
        "worker process %d exited on signal %d",
        "upstream server temporarily disabled",
        "[lua] failed to run rewrite_by_lua*: internal error (no memory?)",
        "no live upstreams while connecting to upstream",
        "failed to load balancer_by_lua_file: %s",
        "limiting requests, excess: %f by zone %s",
        "upstream sent invalid header while reading response header from upstream",
        "upstream sent too big header while reading response header from upstream",
        "client intended to send too large body: %d bytes"
    };
    
    private final String timestamp;
    private final String severity;
    private final String pid;
    private final String clientInfo;
    private final String message;
    private final String connectionId;
    private final String requestId;
    private final String path;
    private final String upstream;

    private IngressErrorLogEntry(String timestamp, String severity, String pid, String clientInfo, 
                               String message, String connectionId, String requestId, String path,
                               String upstream) {
        this.timestamp = timestamp;
        this.severity = severity;
        this.pid = pid;
        this.clientInfo = clientInfo;
        this.message = message;
        this.connectionId = connectionId;
        this.requestId = requestId;
        this.path = path;
        this.upstream = upstream;
    }

    public static IngressErrorLogEntry createRandomEntry(boolean isFrontend) {
        Random random = new Random();
        String timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        String severity = ERROR_LEVELS[random.nextInt(ERROR_LEVELS.length)];
        String pid = String.valueOf(random.nextInt(65536));
        
        String clientIP = generateRandomIP();
        String clientPort = String.valueOf(random.nextInt(60000) + 1024);
        String clientInfo = clientIP + ":" + clientPort;
        
        String message = String.format(
            ERROR_MESSAGES[random.nextInt(ERROR_MESSAGES.length)],
            random.nextInt(10000)
        );
        
        String connectionId = String.valueOf(random.nextInt(10000));
        String requestId = generateRandomRequestId();
        
        String path = isFrontend ? 
            "/api/v1/users/" + random.nextInt(1000) : 
            "/api/v1/data/" + random.nextInt(1000);
            
        String upstream = "upstream: \"" + generateRandomUpstreamAddress() + "\"";
        
        return new IngressErrorLogEntry(timestamp, severity, pid, clientInfo, message, 
                                      connectionId, requestId, path, upstream);
    }
    
    private static String generateRandomIP() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256);
    }
    
    private static String generateRandomRequestId() {
        // Format similar to: aad3c57b59337h7dac58e0ca3b21be51
        StringBuilder sb = new StringBuilder();
        String chars = "abcdef0123456789";
        Random random = new Random();
        for (int i = 0; i < 32; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    private static String generateRandomUpstreamAddress() {
        String[] upstreams = {
            "backend-svc:8080",
            "api-svc:8443",
            "auth-svc:9000",
            "data-svc:8081",
            "cache-svc:6379"
        };
        return upstreams[new Random().nextInt(upstreams.length)];
    }

    @Override
    public String toString() {
        return String.format("%s [%s] %s: %s, client: %s, server: %s, request: \"%s %s\", %s, host: \"%s\"%n",
                timestamp, severity, pid, message, clientInfo, connectionId, "GET", path, upstream, 
                "ingress.kubernetes.example.com");
    }
} 
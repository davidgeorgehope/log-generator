package org.davidgeorgehope;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class NginxLogGenerator {

    // Mapping of specific IP addresses to country codes
    private static final Map<String, String> ipToCountryMap = new HashMap<>();
    private static final List<String> anomalousIPs = Arrays.asList("72.57.0.53");
    private static final List<String> serverIPs = Arrays.asList("203.0.113.5", "203.0.113.6", "203.0.113.7");
    private static final Map<String, String> activeUsers = new HashMap<>();
    private static final String[] usernames = {"alice", "bob", "charlie", "david", "emma", "frank", "grace", "henry"};
    private static final Random random = new Random();

    // Data arrays for random selection
    private static final String[] httpMethods = {"GET", "POST", "PUT", "DELETE"};
    private static final String[] userAgents = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)",
            "Mozilla/5.0 (X11; Linux x86_64)",
            "curl/7.68.0",
            "PostmanRuntime/7.28.4",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 13_5_1 like Mac OS X)",
            "Googlebot/2.1 (+http://www.google.com/bot.html)"
    };
    private static final String[] referrers = {"-", "http://google.com", "http://example.com", "http://bing.com", "http://facebook.com", "http://twitter.com"};
    private static final String[] frontendUrls = {
            "/", "/login", "/register", "/products", "/products/{id}", "/cart", "/checkout",
            "/search?q={query}", "/category/{category}", "/account/orders", "/account/settings",
            "/images/products/{id}.jpg", "/css/main.css", "/js/app.js"
    };
    private static final String[] backendUrls = {
            "/api/products", "/api/products/{id}", "/api/cart", "/api/orders", "/api/users/{id}",
            "/api/auth/login", "/api/auth/register", "/api/payment/process", "/api/shipping/calculate",
            "/api/inventory/check", "/api/recommendations", "/api/reviews/{productId}"
    };

    static {
        ipToCountryMap.put("72.57.0.53", "IN"); // India
        // Add more mappings as needed
    }

    public static void main(String[] args) {
        String frontendLogDir = "logs/frontend";
        String backendLogDir = "logs/backend";

        // Create directories if they don't exist
        new File(frontendLogDir).mkdirs();
        new File(backendLogDir).mkdirs();

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);

        // Generate frontend access logs continuously
        executor.scheduleAtFixedRate(() -> {
            int logsToGenerate = getLogsToGenerate();
            generateAccessLogs(logsToGenerate, frontendLogDir + "/access.log", true);
        }, 0, 1, TimeUnit.SECONDS);

        // Generate backend access logs continuously
        executor.scheduleAtFixedRate(() -> {
            int logsToGenerate = getLogsToGenerate();
            generateAccessLogs(logsToGenerate, backendLogDir + "/access.log", false);
        }, 0, 1, TimeUnit.SECONDS);

        // Generate frontend error logs less frequently
        executor.scheduleAtFixedRate(() -> {
            generateErrorLogs(1, frontendLogDir + "/error.log", true);
        }, 0, 5, TimeUnit.SECONDS);

        // Generate backend error logs less frequently
        executor.scheduleAtFixedRate(() -> {
            generateErrorLogs(1, backendLogDir + "/error.log", false);
        }, 0, 5, TimeUnit.SECONDS);

        int frontendPort = 8080;
        int backendPort = 8081;

        FrontendMetricsServer frontendServer = new FrontendMetricsServer(frontendPort);
        BackendMetricsServer backendServer = new BackendMetricsServer(backendPort);

        frontendServer.start();
        backendServer.start();
    }

    /**
     * Generates Nginx access logs.
     *
     * @param count     Number of logs to generate
     * @param fileName  File to write logs to
     * @param isFrontend Flag indicating whether it's frontend or backend logs
     */
    public static void generateAccessLogs(int count, String fileName, boolean isFrontend) {
        String accessLogTemplate = "%s - %s [%s] \"%s\" %d %d \"%s\" \"%s\" %.3f \"%s\" %s\n";

        try (FileWriter fw = new FileWriter(fileName, true)) { // Append to existing file
            for (int i = 0; i < count; i++) {
                String ip = generateRandomIP(false);
                String username = "-";
                if (random.nextDouble() < 0.7) { // 70% chance of being a logged-in user
                    username = getOrCreateActiveUser(ip);
                }
                String countryCode = getCountryCode(ip);
                String timestamp = generateTimestamp();
                String method = getRandomElement(httpMethods);
                String url = getRandomURL(username, isFrontend);
                String protocol = "HTTP/1.1";
                String request = method + " " + url + " " + protocol;
                int status = getStatusCode(ip, false);
                int size = random.nextInt(5000) + 200;
                String referrer = "-";  // No referrer
                String userAgent = getRandomElement(userAgents);
                double responseTime = generateResponseTime();
                List<String> headersList = generateHeadersList(username);
                String headers = headersList.stream()
                        .map(h -> "\"" + h + "\"")
                        .collect(Collectors.joining(" "));

                String logEntry = String.format(accessLogTemplate, ip, username, timestamp, request,
                        status, size, referrer, userAgent, responseTime, countryCode, headers);

                fw.write(logEntry);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Generates Nginx error logs.
     *
     * @param count     Number of logs to generate
     * @param fileName  File to write logs to
     * @param isFrontend Flag indicating whether it's frontend or backend logs
     */
    public static void generateErrorLogs(int count, String fileName, boolean isFrontend) {
        String errorLogTemplate = "%s [%s] %d#%d: *%d %s, client: %s, server: %s, request: \"%s\", host: \"%s\"\n";

        try (FileWriter fw = new FileWriter(fileName, true)) { // Append to existing file
            for (int i = 0; i < count; i++) {
                String date = generateErrorTimestamp();
                String level = "error";
                int pid = random.nextInt(10000) + 1000;
                int tid = random.nextInt(10);
                int connection = random.nextInt(10000);
                String clientIP = generateRandomIP(false);
                String server = isFrontend ? "frontend.example.com" : "api.example.com";
                String method = getRandomElement(httpMethods);
                String url = getRandomURL("-", isFrontend);
                String protocol = "HTTP/1.1";
                String request = method + " " + url + " " + protocol;
                String host = server;
                String message = getRandomErrorMessage(isFrontend, url, clientIP, server, request);

                String logEntry = String.format(errorLogTemplate, date, level, pid, tid,
                        connection, message, clientIP, server,
                        request, host);

                fw.write(logEntry);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Generates a random IP address.
     *
     * @param includeAnomalous Whether to include anomalous IPs
     * @return Random IP address as a string
     */
    static String generateRandomIP(boolean includeAnomalous) {
        if (includeAnomalous && random.nextInt(100) < 50) { // 50% chance
            return anomalousIPs.get(random.nextInt(anomalousIPs.size()));
        } else if (!includeAnomalous && random.nextInt(100) < 10) { // 10% chance in normal logs
            return anomalousIPs.get(random.nextInt(anomalousIPs.size()));
        } else {
            return random.nextInt(256) + "." + random.nextInt(256) + "." +
                    random.nextInt(256) + "." + random.nextInt(256);
        }
    }

    /**
     * Gets country code based on IP address.
     *
     * @param ip IP address
     * @return Country code
     */
    static String getCountryCode(String ip) {
        return ipToCountryMap.getOrDefault(ip, "US"); // Default to "US"
    }

    /**
     * Generates a timestamp in Nginx log format.
     *
     * @return Timestamp as a string
     */
    static String generateTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");
        return ZonedDateTime.now().format(formatter);
    }

    /**
     * Gets HTTP status code based on IP and whether it's anomalous.
     *
     * @param ip          IP address
     * @param isAnomalous Whether the log is anomalous
     * @return HTTP status code
     */
    static int getStatusCode(String ip, boolean isAnomalous) {
        if (anomalousIPs.contains(ip)) {
            return getRandomErrorStatusCode();
        } else {
            return getRandomStatusCode(isAnomalous);
        }
    }

    /**
     * Generates a random success status code.
     *
     * @return HTTP status code
     */
    static int getRandomSuccessStatusCode() {
        int chance = random.nextInt(100);
        if (chance < 90) {
            return 200; // 90% chance
        } else {
            return 201; // 10% chance
        }
    }

    /**
     * Generates a random error status code.
     *
     * @return HTTP status code
     */
    static int getRandomErrorStatusCode() {
        int chance = random.nextInt(100);
        if (chance < 80) {
            return 404; // 80% chance of 404
        } else if (chance < 90) {
            return 403; // 10% chance of 403
        } else {
            return 500; // 10% chance of 500
        }
    }

    /**
     * Generates a random status code with weighted probabilities.
     *
     * @param isAnomalous Whether the log is anomalous
     * @return HTTP status code
     */
    static int getRandomStatusCode(boolean isAnomalous) {
        int chance = random.nextInt(100);
        if (isAnomalous) {
            if (chance < 70) {
                return getRandomErrorStatusCode(); // 70% chance of error
            } else {
                return getRandomSuccessStatusCode();
            }
        } else {
            if (chance < 90) {
                return getRandomSuccessStatusCode(); // 90% chance of success
            } else {
                return getRandomErrorStatusCode();
            }
        }
    }

    /**
     * Generates a random response time between 0.100 and 1.500 seconds.
     *
     * @return Response time as a double
     */
    static double generateResponseTime() {
        // Generate response time between 0.100 and 1.500 seconds
        double responseTime = 0.100 + (1.500 - 0.100) * random.nextDouble();
        return Double.parseDouble(String.format("%.3f", responseTime));
    }

    /**
     * Generates a random URL.
     *
     * @param username    Username for personalization
     * @param isFrontend  Whether it's frontend or backend
     * @return URL string
     */
    static String getRandomURL(String username, boolean isFrontend) {
        String[] urls = isFrontend ? frontendUrls : backendUrls;
        String baseUrl = getRandomElement(urls);

        if (username.equals("-")) {
            // Non-logged in users are more likely to visit public pages
            if (isFrontend) {
                baseUrl = getRandomElement(new String[]{"/", "/products", "/search", "/login", "/register"});
            } else {
                baseUrl = getRandomElement(new String[]{"/api/products", "/api/auth/login", "/api/auth/register"});
            }
        }

        // Replace placeholders with random values
        baseUrl = replaceUrlPlaceholders(baseUrl);

        return baseUrl;
    }

    /**
     * Replaces placeholders in URLs with random values.
     *
     * @param baseUrl URL with placeholders
     * @return URL with placeholders replaced
     */
    private static String replaceUrlPlaceholders(String baseUrl) {
        if (baseUrl.contains("{id}")) {
            baseUrl = baseUrl.replace("{id}", String.valueOf(random.nextInt(1000) + 1));
        }
        if (baseUrl.contains("{category}")) {
            baseUrl = baseUrl.replace("{category}", getRandomElement(new String[]{"electronics", "clothing", "books", "home-garden"}));
        }
        if (baseUrl.contains("{query}")) {
            baseUrl = baseUrl.replace("{query}", getRandomElement(new String[]{"laptop", "smartphone", "headphones", "camera"}));
        }
        if (baseUrl.contains("{productId}")) {
            baseUrl = baseUrl.replace("{productId}", String.valueOf(random.nextInt(1000) + 1));
        }
        if (baseUrl.equals("/api/payment/process")) {
            baseUrl += "?amount=" + ((random.nextInt(10000) + 1000) / 100.0);
        }
        if (baseUrl.equals("/api/shipping/calculate")) {
            baseUrl += "?weight=" + ((random.nextInt(1000) + 100) / 100.0);
        }
        if (baseUrl.equals("/api/inventory/check")) {
            baseUrl += "?productId=" + (random.nextInt(1000) + 1) + "&quantity=" + (random.nextInt(10) + 1);
        }
        return baseUrl;
    }

    /**
     * Generates a list of headers as strings.
     *
     * @param username Username for personalized headers
     * @return List of headers
     */
    static List<String> generateHeadersList(String username) {
        List<String> headers = new ArrayList<>();
        headers.add("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        headers.add("Accept-Encoding: gzip, deflate, br");
        headers.add("Accept-Language: en-US,en;q=0.5");

        if (!username.equals("-")) {
            headers.add("Authorization: Bearer " + generateRandomString(32));
            headers.add("X-User-ID: " + username);
        }
        if (random.nextDouble() < 0.5) {
            headers.add("X-Request-ID: " + UUID.randomUUID().toString());
        }
        if (random.nextDouble() < 0.3) {
            headers.add("X-Forwarded-For: " + generateRandomIP(false));
        }

        return headers;
    }

    /**
     * Generates an error timestamp in Nginx log format.
     *
     * @return Error timestamp as a string
     */
    static String generateErrorTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        return ZonedDateTime.now().format(formatter);
    }

    /**
     * Gets or creates an active user associated with an IP address.
     *
     * @param ip IP address
     * @return Username
     */
    static String getOrCreateActiveUser(String ip) {
        if (!activeUsers.containsKey(ip)) {
            activeUsers.put(ip, getRandomElement(usernames));
        }
        return activeUsers.get(ip);
    }

    /**
     * Generic method to get a random element from an array.
     *
     * @param array Array of elements
     * @param <T>   Type of elements
     * @return Random element
     */
    static <T> T getRandomElement(T[] array) {
        return array[random.nextInt(array.length)];
    }

    /**
     * Generates the number of logs to generate based on the time of day.
     *
     * @return Number of logs to generate
     */
    static int getLogsToGenerate() {
        LocalTime now = LocalTime.now();
        int hour = now.getHour();

        // Simulate higher traffic during business hours
        if (hour >= 9 && hour < 17) {
            return random.nextInt(11) + 10; // 10-20 logs per second
        } else if ((hour >= 7 && hour < 9) || (hour >= 17 && hour < 20)) {
            return random.nextInt(6) + 5; // 5-10 logs per second
        } else {
            return random.nextInt(3) + 1; // 1-3 logs per second
        }
    }

    /**
     * Generates a random error message.
     *
     * @param isFrontend Whether it's frontend or backend
     * @param url        URL causing the error
     * @param clientIP   Client IP address
     * @param server     Server name
     * @param request    Request line
     * @return Error message
     */
    static String getRandomErrorMessage(boolean isFrontend, String url, String clientIP, String server, String request) {
        String[] frontendErrorMessages = {
                "open() \"%s\" failed (2: No such file or directory)",
                "directory index of \"%s\" is forbidden",
                "access forbidden by rule"
        };

        String[] backendErrorMessages = {
                "connect() failed (111: Connection refused) while connecting to upstream",
                "upstream timed out (110: Connection timed out) while reading response header from upstream",
                "no live upstreams while connecting to upstream"
        };

        String[] errorMessages = isFrontend ? frontendErrorMessages : backendErrorMessages;
        String messageTemplate = getRandomElement(errorMessages);

        String message;
        if (isFrontend) {
            message = String.format(messageTemplate, url);
        } else {
            message = messageTemplate;
        }

        return message;
    }

    /**
     * Generates a random alphanumeric string of given length.
     *
     * @param length Length of the string
     * @return Random string
     */
    static String generateRandomString(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }
        return sb.toString();
    }
}
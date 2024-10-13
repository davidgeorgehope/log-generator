package org.davidgeorgehope;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class LogGeneratorUtils {
    private static final Map<String, String> ipToCountryMap = new HashMap<>();
    private static final List<String> anomalousIPs = Arrays.asList("72.57.0.53");
    private static final String[] userAgents = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)",
            "Mozilla/5.0 (X11; Linux x86_64)",
            "curl/7.68.0",
            "PostmanRuntime/7.28.4",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 13_5_1 like Mac OS X)",
            "Googlebot/2.1 (+http://www.google.com/bot.html)"
    };
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
    public static final String anomalousHighRequestIP = "192.0.2.1"; // Reserved IP for documentation
    private static final Random RANDOM = new Random();

    static {
        ipToCountryMap.put("72.57.0.53", "IN"); // India
        // Add more mappings as needed
    }

    public static String generateRandomIP(boolean includeAnomalous) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (AnomalyConfig.isInduceHighRequestRateFromSingleIP()) {
            // Generate majority of traffic from a single IP
            if (random.nextInt(100) < 80) { // 80% chance
                return anomalousHighRequestIP;
            } else {
                // Generate a random IP
                return random.nextInt(256) + "." + random.nextInt(256) + "." +
                        random.nextInt(256) + "." + random.nextInt(256);
            }
        }

        if (includeAnomalous && random.nextInt(100) < 50) { // 50% chance
            return getRandomElement(anomalousIPs);
        } else if (!includeAnomalous && random.nextInt(100) < 10) { // 10% chance in normal logs
            return getRandomElement(anomalousIPs);
        } else {
            return random.nextInt(256) + "." + random.nextInt(256) + "." +
                    random.nextInt(256) + "." + random.nextInt(256);
        }
    }

    public static String getCountryCode(String ip) {
        return ipToCountryMap.getOrDefault(ip, "US"); // Default to "US"
    }

    public static HttpMethod getRandomHttpMethod() {
        return HttpMethod.values()[ThreadLocalRandom.current().nextInt(HttpMethod.values().length)];
    }

    public static String getRandomUserAgent() {
        return getRandomElement(userAgents);
    }

    public static String getRandomURL(String username, boolean isFrontend) {
        String[] urls = isFrontend ? frontendUrls : backendUrls;
        String baseUrl = getRandomElement(urls);

        ThreadLocalRandom random = ThreadLocalRandom.current();

        // If inducing high distinct URLs from single IP
        if (AnomalyConfig.isInduceHighDistinctURLsFromSingleIP()) {
            if (UserSessionManager.currentIP.equals(anomalousHighRequestIP)) {
                // Access a wide range of URLs
                baseUrl = getRandomElement(urls);
            }
        }

        // Replace placeholders with random values
        baseUrl = replaceUrlPlaceholders(baseUrl);
        return baseUrl;
    }

    private static String replaceUrlPlaceholders(String baseUrl) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

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

    public static List<String> generateHeadersList(String username) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

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

    public static int getStatusCode(String ip, boolean isAnomalous) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (AnomalyConfig.isInduceDatabaseOutage()) {
            // Force 100% 500 Internal Server Errors
            return 500;
        }

        if (AnomalyConfig.isInduceHighErrorRate()) {
            // Increase the chance of errors
            if (random.nextInt(100) < 70) { // 70% chance of error
                return getRandomErrorStatusCode();
            } else {
                return getRandomSuccessStatusCode();
            }
        }

        // Existing logic
        if (anomalousIPs.contains(ip)) {
            return getRandomErrorStatusCode();
        } else {
            return getRandomStatusCode(isAnomalous);
        }
    }

    private static int getRandomSuccessStatusCode() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int chance = random.nextInt(100);
        if (chance < 90) {
            return 200; // 90% chance
        } else {
            return 201; // 10% chance
        }
    }

    private static int getRandomErrorStatusCode() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int chance = random.nextInt(100);
        if (chance < 80) {
            return 404; // 80% chance of 404
        } else if (chance < 90) {
            return 403; // 10% chance of 403
        } else {
            return 500; // 10% chance of 500
        }
    }

    private static int getRandomStatusCode(boolean isAnomalous) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
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

    public static double generateResponseTime() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        // Generate response time between 0.100 and 1.500 seconds
        return 0.100 + (1.500 - 0.100) * random.nextDouble();
    }

    public static int getLogsToGenerate() {
        double meanRequestsPerSecond = 50; // Normal mean request rate
        if (AnomalyConfig.isInduceHighVisitorRate()) {
            meanRequestsPerSecond = 500; // Increase mean request rate during high visitor rate anomaly
        } else if (AnomalyConfig.isInduceLowRequestRate()) {
            meanRequestsPerSecond = 5; // Decrease mean request rate during low request rate anomaly
        }
        return (int) Math.round(-Math.log(1 - RANDOM.nextDouble()) * meanRequestsPerSecond);
    }

    public static String getRandomErrorMessage(boolean isFrontend, String url) {
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

        if (isFrontend) {
            return String.format(messageTemplate, url);
        } else {
            return messageTemplate;
        }
    }

    public static String generateRandomString(int length) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }
        return sb.toString();
    }

    public static <T> T getRandomElement(T[] array) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException("Array must not be null or empty");
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return array[random.nextInt(array.length)];
    }

    public static <T> T getRandomElement(List<T> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("List must not be null or empty");
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return list.get(random.nextInt(list.size()));
    }

    public static String getRandomMySQLErrorMessage(int errorCode) {
        Map<Integer, String[]> errorMessages = new HashMap<>();
        errorMessages.put(1045, new String[]{
                "Access denied for user 'ecommerce_user'@'localhost' (using password: YES)",
                "Access denied for user 'shop_admin'@'localhost' (using password: NO)"
        });
        errorMessages.put(1146, new String[]{
                "Table 'ecommerce_db.products' doesn't exist",
                "Table 'ecommerce_db.orders' doesn't exist"
        });
        errorMessages.put(2003, new String[]{
                "Can't connect to MySQL server on 'db.ecommerce.com' (10061)",
                "Can't connect to MySQL server on '192.168.1.100' (111)"
        });
        errorMessages.put(1064, new String[]{
                "You have an error in your SQL syntax; check the manual near 'SELECT * FROM products WHERE category = Electronics'",
                "Syntax error near 'INSERT INTO orders (customer_id, product_id, quantity) VALUE'"
        });
        errorMessages.put(1054, new String[]{
                "Unknown column 'discount_price' in 'field list'",
                "Unknown column 'customer_email' in 'where clause'"
        });

        String[] messages = errorMessages.getOrDefault(errorCode, new String[]{"An unknown error occurred"});
        return getRandomElement(messages);
    }

    public static String getRandomSlowQuerySQL() {
        String[] sqlQueries = {
                "SELECT * FROM orders WHERE customer_id = {id}",
                "UPDATE products SET stock = stock - 1 WHERE product_id = {id}",
                "INSERT INTO user_sessions (session_id, user_id) VALUES ('{session}', {id})",
                "DELETE FROM carts WHERE created_at < NOW() - INTERVAL 30 DAY",
                "SELECT p.* FROM products p JOIN categories c ON p.category_id = c.id WHERE c.name = '{category}'"
        };

        String sql = getRandomElement(sqlQueries);
        sql = replaceSQLPlaceholders(sql);
        return sql;
    }

    private static String replaceSQLPlaceholders(String sql) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (sql.contains("{id}")) {
            sql = sql.replace("{id}", String.valueOf(random.nextInt(1, 10000)));
        }
        if (sql.contains("{session}")) {
            sql = sql.replace("{session}", generateRandomString(32));
        }
        if (sql.contains("{category}")) {
            sql = sql.replace("{category}", getRandomElement(new String[]{"Electronics", "Books", "Clothing", "Home & Garden"}));
        }
        return sql;
    }

    
}
package org.davidgeorgehope;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private static List<String> ipPool;

    // First octets for USA IP ranges
    private static final List<Integer> usaIpFirstOctets = List.of(3, 4, 12, 13, 17);

    // First octets for Europe IP ranges
    private static final List<Integer> europeIpFirstOctets = List.of(51, 77, 78, 79);

    static {
        ipToCountryMap.put("72.57.0.53", "IN"); // India
        // Add more mappings as needed
    }

    private static List<String> generateIpPool() {
        List<String> ips = new ArrayList<>();
        
        // Total number of IPs to generate
        int totalIps = 1000;
        
        // Number of IPs per region
        int ipsPerRegion = totalIps / 2;
        
        // Generate USA IPs
        for (int i = 0; i < ipsPerRegion; i++) {
            String ip = generateRandomIp(usaIpFirstOctets);
            ips.add(ip);
        }

        // Generate Europe IPs
        for (int i = 0; i < ipsPerRegion; i++) {
            String ip = generateRandomIp(europeIpFirstOctets);
            ips.add(ip);
        }

        return ips;
    }

    private static String generateRandomIp(List<Integer> firstOctetPool) {
        int firstOctet = firstOctetPool.get(RANDOM.nextInt(firstOctetPool.size()));
        int secondOctet = RANDOM.nextInt(256);
        int thirdOctet = RANDOM.nextInt(256);
        int fourthOctet = RANDOM.nextInt(256);
        return firstOctet + "." + secondOctet + "." + thirdOctet + "." + fourthOctet;
    }

    public static String generateRandomIP(boolean includeAnomalous) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<String> ipPool = getIpPool();

        if (AnomalyConfig.isInduceHighRequestRateFromSingleIP()) {
            // Generate a Zipf-distributed index
            int size = ipPool.size();
            double exponent = 1.0; // Adjust the exponent as needed
            double harmonic = 0.0;

            // Calculate the harmonic number for normalization
            for (int i = 1; i <= size; i++) {
                harmonic += 1.0 / Math.pow(i, exponent);
            }

            // Generate a random value
            double rand = random.nextDouble();

            // Find the index corresponding to the random value
            double cumulativeProbability = 0.0;
            int index = 0;
            for (int i = 1; i <= size; i++) {
                cumulativeProbability += (1.0 / Math.pow(i, exponent)) / harmonic;
                if (rand <= cumulativeProbability) {
                    index = i - 1;
                    break;
                }
            }

            // Ensure the anomalous IP is at the top
            if (index == 0) {
                return anomalousHighRequestIP;
            } else {
                return ipPool.get(index % ipPool.size());
            }
        } else {
            // Normal operation
            return ipPool.get(random.nextInt(ipPool.size()));
        }
    }

    public static synchronized List<String> getIpPool() {
        if (ipPool == null) {
            ipPool = generateIpPool();
        }
        return ipPool;
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
        String baseUrl;

        // If inducing high distinct URLs from single IP
        if (AnomalyConfig.isInduceHighDistinctURLsFromSingleIP()
            && UserSessionManager.currentIP.equals(anomalousHighRequestIP)) {
            // Access a wide range of URLs by iterating through the list
            baseUrl = urls[RANDOM.nextInt(urls.length)];
        } else {
            // Normal behavior
            baseUrl = getRandomElement(urls);
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

    private static Integer cachedErrorStatusCode = null;

    public static int getStatusCode(String ip, boolean isAnomalous) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (AnomalyConfig.isInduceDatabaseOutage()) {
            // Select a specific error code for database outage
            return 500;
        }

        if (AnomalyConfig.isInduceHighErrorRate()) {
            // During high error rate anomaly
            if (cachedErrorStatusCode == null) {
                // Select a random error status code once and cache it
                cachedErrorStatusCode = getRandomErrorStatusCode();
            }
            // Use the cached error status code for all requests
            return cachedErrorStatusCode;
        }

        // Reset the cached error status code when anomaly is not active
        cachedErrorStatusCode = null;

        // Existing logic
        if (anomalousIPs.contains(ip) || isAnomalous) {
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
        // Define possible error status codes
        int[] errorCodes = {400, 401, 403, 404, 500, 502, 503, 504};
        return errorCodes[ThreadLocalRandom.current().nextInt(errorCodes.length)];
    }

    private static int getRandomStatusCode(boolean isAnomalous) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int chance = random.nextInt(100);
        if (chance < 90) {
            return getRandomSuccessStatusCode(); // 90% chance of success
        } else {
            return getRandomErrorStatusCode();   // 10% chance of error
        }
    }

    public static double generateResponseTime() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        // Generate response time between 0.100 and 1.500 seconds
        return 0.100 + (1.500 - 0.100) * random.nextDouble();
    }

    public static int getLogsToGenerate() {
        double meanRequestsPerSecond = 50;
        if (AnomalyConfig.isInduceHighVisitorRate()) {
            meanRequestsPerSecond = 500;
        } else if (AnomalyConfig.isInduceLowRequestRate()) {
            meanRequestsPerSecond = 5;
        }

        // Parameters for the Pareto distribution
        double scale = 1.0;
        double shape = 1.5; // Lower values produce heavier tails

        // Generate a Pareto-distributed random number
        double u = RANDOM.nextDouble();
        double paretoValue = scale / Math.pow(u, 1.0 / shape);

        // Adjust the mean requests per second based on the Pareto value
        int logsToGenerate = (int) Math.round(meanRequestsPerSecond * paretoValue);

        return logsToGenerate;
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

    public static String getRandomElement(String[] array) {
        return array[ThreadLocalRandom.current().nextInt(array.length)];
    }



    public static String generateRandomSessionId() {
        // Generate a random session ID (alphanumeric string)
        return java.util.UUID.randomUUID().toString().replaceAll("-", "");
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}

package client;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QueryClient {

    private int lamportClock = 0;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 3000;
    private static final Logger logger = Logger.getLogger(QueryClient.class.getName());

    /**
     * Main method to run the QueryClient.
     * Parses command line arguments, creates a connection, and sends a GET request
     * with retries.
     *
     * @param args Command line arguments: server URL and optional station ID.
     */
    public static void main(String[] args) {
        QueryClient client = new QueryClient();
        try {
            ServerConfig config = client.parseCommandLineArgs(args);
            HttpURLConnection connection = client.createConnection(config.serverUrl, config.stationId);
            client.sendGetRequestWithRetry(connection);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /**
     * Sends a GET request to the server and processes the response.
     * Increments the Lamport clock before sending the request and after receiving
     * the response.
     *
     * @param connection The HttpURLConnection object for the GET request.
     * @throws IOException If an I/O error occurs.
     */
    public void sendGetRequest(HttpURLConnection connection) throws IOException {
        incrementLamportClock(); // Increment before sending the request
        logger.info("Sending GET request to: " + connection.getURL());
        connection.setRequestProperty("Lamport-Clock", String.valueOf(lamportClock));

        int responseCode = connection.getResponseCode();
        String serverClockStr = connection.getHeaderField("Lamport-Clock");
        if (serverClockStr != null) {
            int serverClock = Integer.parseInt(serverClockStr);
            lamportClock = Math.max(lamportClock, serverClock) + 1;
            logger.info("Updated Lamport clock to: " + lamportClock);
        } else {
            incrementLamportClock(); // Fallback if server doesn't send a clock
        }

        logger.info("Received response code: " + responseCode);

        if (responseCode == 200) {
            incrementLamportClock(); // Increment before processing the response
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            String responseString = response.toString();
            logger.info("Received response: " + responseString + "\n");
            displayWeatherData(responseString);
        } else {
            logger.warning("Error: Server returned status code " + responseCode);
            System.out.println("Error: Server returned status code " + responseCode);
        }
    }

    /**
     * Sends a GET request with retries in case of failure.
     * Retries the request up to MAX_RETRIES times with a delay between retries.
     *
     * @param connection The HttpURLConnection object for the GET request.
     * @throws IOException If all retries fail.
     */
    public void sendGetRequestWithRetry(HttpURLConnection connection) throws IOException {
        int retries = 0;
        IOException lastException = null;
        String url = connection.getURL().toString();
        logger.info("Starting GET request for URL: " + url);

        while (retries <= MAX_RETRIES) {
            try {
                if (retries == 0) {
                    logger.info("Initial attempt");
                } else {
                    logger.info("Retry " + retries);
                    incrementLamportClock(); // Increment before creating a new connection
                    connection = createConnection(url);
                }
                sendGetRequest(connection);
                return; // If successful, exit the method
            } catch (IOException e) {
                lastException = e;
                logger.log(Level.WARNING, "Request failed. Exception: " + e.getMessage());
                incrementLamportClock(); // Increment after receiving a failed response
                retries++;
                if (retries > MAX_RETRIES) {
                    break; // Exit the loop if max retries reached
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }
        if (lastException != null) {
            logger.log(Level.SEVERE, "All retries failed");
            throw lastException;
        }
    }

    /**
     * Creates an HttpURLConnection for a GET request to the specified server URL
     * and station ID.
     * Increments the Lamport clock before creating the connection.
     *
     * @param serverUrl The server URL.
     * @param stationId The station ID (optional).
     * @return The HttpURLConnection object.
     * @throws IOException If an I/O error occurs.
     */
    public HttpURLConnection createConnection(String serverUrl, String stationId) throws IOException {
        String urlString = serverUrl + "/weather.json" + (stationId != null ? "?id=" + stationId : "");
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        incrementLamportClock(); // Increment before creating the connection
        connection.setRequestProperty("Lamport-Clock", String.valueOf(lamportClock));
        return connection;
    }

    /**
     * Creates an HttpURLConnection for a GET request to the specified server URL.
     * Increments the Lamport clock before creating the connection.
     *
     * @param serverUrl The server URL.
     * @return The HttpURLConnection object.
     * @throws IOException If an I/O error occurs.
     */
    public HttpURLConnection createConnection(String serverUrl) throws IOException {
        URL url = new URL(serverUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        incrementLamportClock();
        connection.setRequestProperty("Lamport-Clock", String.valueOf(lamportClock));
        return connection;
    }

    /**
     * Parses command line arguments to extract the server URL and optional station
     * ID.
     *
     * @param args Command line arguments.
     * @return A ServerConfig object containing the server URL and station ID.
     * @throws IllegalArgumentException If the server URL is not provided.
     */
    public ServerConfig parseCommandLineArgs(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: java QueryClient <server-url> [station-id]");
        }
        String serverUrl = args[0];
        String stationId = args.length > 1 ? args[1] : null;
        return new ServerConfig(serverUrl, stationId);
    }

    /**
     * Displays weather data in JSON format.
     * Handles both JSON arrays and single JSON objects.
     *
     * @param jsonData The JSON data as a string.
     */
    private void displayWeatherData(String jsonData) {
        jsonData = jsonData.trim();
        if (jsonData.startsWith("[") && jsonData.endsWith("]")) {
            // It's an array, so we'll display each object
            jsonData = jsonData.substring(1, jsonData.length() - 1);
            String[] objects = jsonData.split("\\},\\{");
            for (int i = 0; i < objects.length; i++) {
                String object = objects[i];
                if (!object.startsWith("{"))
                    object = "{" + object;
                if (!object.endsWith("}"))
                    object = object + "}";
                System.out.println("Weather Data " + (i + 1) + ":");
                displayJsonObject(object);
                System.out.println(); // Add a blank line between objects
            }
        } else if (jsonData.startsWith("{") && jsonData.endsWith("}")) {
            // It's a single object
            displayJsonObject(jsonData);
        } else {
            System.out.println("Error: Invalid JSON data");
        }
    }

    /**
     * Displays a single JSON object.
     * Parses the JSON object and prints each key-value pair.
     *
     * @param jsonObject The JSON object as a string.
     */
    private void displayJsonObject(String jsonObject) {
        jsonObject = jsonObject.substring(1, jsonObject.length() - 1);
        String[] pairs = jsonObject.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0].trim().replace("\"", "");
                String value = keyValue[1].trim().replace("\"", "");
                System.out.println(key + ": " + value);
            }
        }
    }

    /**
     * Gets the current value of the Lamport clock.
     *
     * @return The current Lamport clock value.
     */
    public int getLamportClock() {
        return lamportClock;
    }

    /**
     * Increments the Lamport clock by 1.
     */
    private void incrementLamportClock() {
        lamportClock++;
        logger.info("Lamport clock incremented to: " + lamportClock);
    }

    /**
     * A class to hold server configuration details.
     */
    public static class ServerConfig {
        public final String serverUrl;
        public final String stationId;

        /**
         * Constructor for ServerConfig.
         *
         * @param serverUrl The server URL.
         * @param stationId The station ID (optional).
         */
        public ServerConfig(String serverUrl, String stationId) {
            this.serverUrl = serverUrl;
            this.stationId = stationId;
        }
    }
}
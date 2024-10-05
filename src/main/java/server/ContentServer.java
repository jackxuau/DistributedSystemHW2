package server;

import com.google.gson.Gson;
import pojo.WeatherPojo;
import org.apache.commons.lang3.StringUtils;

import java.io.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ContentServer {
    private static final Logger logger = Logger.getLogger(ContentServer.class.getName());
    private int lamportClock = 0;
    private final String serverUrl;
    private final String filePath;
    private final Gson gson = new Gson();

    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 3000;
    private static final int UPDATE_INTERVAL_SECONDS = 3; // Interval for periodic updates
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * Constructor for ContentServer.
     * Initializes the server URL and file path.
     *
     * @param serverUrl The URL of the server to which data will be uploaded.
     * @param filePath  The path of the file containing weather data.
     */
    public ContentServer(String serverUrl, String filePath) {
        this.serverUrl = serverUrl;
        this.filePath = filePath;
    }

    /**
     * Synchronized method to increment the Lamport clock.
     */
    private synchronized void incrementClock() {
        lamportClock++;
    }

    /**
     * Synchronized method to update the Lamport clock based on the received clock.
     *
     * @param receivedClock The Lamport clock value received from the server.
     */
    private synchronized void updateClock(int receivedClock) {
        lamportClock = Math.max(lamportClock, receivedClock) + 1;
    }

    /**
     * Uploads weather data to the server.
     * Reads data from a file, parses it, converts it to JSON, and sends it to the
     * server.
     * Retries the upload up to MAX_RETRIES times in case of failure.
     *
     * @throws IOException              If an I/O error occurs.
     * @throws IllegalArgumentException If the data format is invalid.
     */
    public void uploadData() throws IOException, IllegalArgumentException {
        incrementClock(); // Local event: starting to process
        String fileContent = "";

        if (StringUtils.isEmpty(fileContent)) {
            // No content to send
            HttpURLConnection connection = createConnection();
            sendPutRequest(connection, "");
            return;
        }

        incrementClock(); // Local event: parsing data
        Map<String, String> weatherData = parseWeatherData(fileContent);

        incrementClock(); // Local event: converting to JSON
        String jsonData = gson.toJson(weatherData);

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                HttpURLConnection connection = createConnection();
                sendPutRequest(connection, jsonData);
                verifyUploadedData(connection, weatherData);
                break;
            } catch (IOException e) {
                logger.warning("Attempt " + (attempt + 1) + " failed: " + e.getMessage());
                if (attempt == MAX_RETRIES - 1) {
                    throw e;
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }
    }


    private String readWeatherDataFromFile() throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    /**
     * Parses weather data from a string.
     *
     * @param data The weather data as a string.
     * @return A map containing the parsed weather data.
     * @throws IllegalArgumentException If the data format is invalid.
     */
    private Map<String, String> parseWeatherData(String data) throws IllegalArgumentException {
        Map<String, String> weatherData = new HashMap<>();
        String[] lines = data.split("\n");
        for (String line : lines) {
            String[] parts = line.split(":", 2); // Split only on the first colon
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
                    throw new IllegalArgumentException("Invalid data format: empty key or value");
                }
                weatherData.put(key, value);
            } else {
                throw new IllegalArgumentException("Invalid data format: " + line);
            }
        }
        if (!weatherData.containsKey("id")) {
            throw new IllegalArgumentException("Missing required field: id");
        }
        return weatherData;
    }

    /**
     * Creates an HTTP connection to the server.
     *
     * @return The HTTP connection.
     * @throws IOException If an I/O error occurs.
     */
    public HttpURLConnection createConnection() throws IOException {
        URL url = new URL(serverUrl + "/weather.json");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Lamport-Clock", String.valueOf(lamportClock));
        connection.setRequestProperty("User-Agent", "ATOMClient/1/0");
        connection.setDoOutput(true);
        return connection;
    }

    /**
     * Sends a PUT request to the server with the given JSON data.
     *
     * @param connection The HTTP connection.
     * @param jsonData   The JSON data to be sent.
     * @throws IOException If an I/O error occurs.
     */
    public void sendPutRequest(HttpURLConnection connection, String jsonData) throws IOException {
        incrementClock(); // Before sending message
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Content-Length", String.valueOf(jsonData.getBytes().length));
        connection.setRequestProperty("Lamport-Clock", String.valueOf(lamportClock));

        if (!jsonData.isEmpty()) {
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonData.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
        }

        int responseCode = connection.getResponseCode();
        String serverClock = connection.getHeaderField("Lamport-Clock");
        if (serverClock != null) {
            updateClock(Integer.parseInt(serverClock)); // Upon receiving response
        }

        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_CREATED
                && responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
            throw new IOException("PUT request failed. Response Code: " + responseCode);
        }
    }

    /**
     * Verifies that the uploaded data matches the data on the server.
     *
     * @param connection   The HTTP connection.
     * @param originalData The original weather data.
     * @throws IOException If an I/O error occurs.
     */
    public void verifyUploadedData(HttpURLConnection connection, Map<String, String> originalData)
            throws IOException {
        incrementClock(); // Local event: starting verification
        URL getUrl = new URL(serverUrl + "/weather.json");
        HttpURLConnection getConnection = (HttpURLConnection) getUrl.openConnection();
        getConnection.setRequestMethod("GET");
        getConnection.setRequestProperty("User-Agent", "ATOMClient/1/0");
        getConnection.setRequestProperty("Lamport-Clock", String.valueOf(lamportClock));

        String serverClock = getConnection.getHeaderField("Lamport-Clock");
        if (serverClock != null) {
            updateClock(Integer.parseInt(serverClock)); // Upon receiving response
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(getConnection.getInputStream()))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        }
        String jsonResponse = response.toString();

        // Log the response for debugging
        System.out.println("Server response: " + jsonResponse);


        Map<String, String> retrievedData = parseJSONResponse(jsonResponse);
        System.out.println("Original data: " + originalData);
        System.out.println("Retrieved data: " + retrievedData);

        if (!originalData.equals(retrievedData)) {
            System.out.println("Data mismatch. Differences:");
            for (String key : originalData.keySet()) {
                if (!retrievedData.containsKey(key) || !originalData.get(key).equals(retrievedData.get(key))) {
                    System.out.println(
                            key + ": Original=" + originalData.get(key) + ", Retrieved=" + retrievedData.get(key));
                }
            }
            for (String key : retrievedData.keySet()) {
                if (!originalData.containsKey(key)) {
                    System.out.println(key + ": Original=null, Retrieved=" + retrievedData.get(key));
                }
            }
            throw new IOException("Uploaded data does not match the data on the server");
        }
    }

    /**
     * Parses a JSON response string into a map of weather data.
     *
     * @param jsonResponse The JSON response string.
     * @return A map containing the parsed weather data.
     */
    private Map<String, String> parseJSONResponse(String jsonResponse) {

        // Check if the response is an array
        if (jsonResponse.trim().startsWith("[") && jsonResponse.trim().endsWith("]")) {
            // Remove the square brackets
            jsonResponse = jsonResponse.substring(1, jsonResponse.length() - 1);
        }

        WeatherPojo wd = gson.fromJson(jsonResponse, WeatherPojo.class);
        Map<String, String> data = new HashMap<>();
//            data.put("id", wd.getId());
//            data.put("name", wd.getName());
//            for (Map.Entry<String, Object> entry : wd.getData().entrySet()) {
//                Object value = entry.getValue();
//                if (value instanceof Number) {
//                    // Check if the number is an integer
//                    if (value instanceof Integer || (value instanceof Double && ((Double) value) % 1 == 0)) {
//                        data.put(entry.getKey(), String.valueOf(((Number) value).intValue()));
//                    } else {
//                        data.put(entry.getKey(), value.toString());
//                    }
//                } else {
//                    data.put(entry.getKey(), value.toString());
//                }
//            }
        return data;
    }

    /**
     * Increments the Lamport clock.
     */
    public void incrementLamportClock() {
        lamportClock++;
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
     * The main method to start the ContentServer.
     *
     * @param args Command line arguments. The first argument is the server URL, and
     *             the second is the file path.
     * @throws IOException              If an I/O error occurs.
     * @throws IllegalArgumentException If the data format is invalid.
     */
    public static void main(String[] args) throws IOException, IllegalArgumentException {
        if (args.length != 2) {
            System.err.println("Usage: java ContentServer <server_url> <file_path>");
            System.exit(1);
        }

        ContentServer contentServer = new ContentServer(args[0], args[1]);
        contentServer.schedulePeriodicUpdates(); // Start periodic updates

        // Keep the main thread alive to allow periodic tasks to run
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Schedules periodic data uploads to the server.
     */
    public void schedulePeriodicUpdates() {
        Runnable uploadTask = () -> {
            try {
                uploadData();
                logger.info("Periodic data upload successful.");
            } catch (IOException | IllegalArgumentException e) {
                logger.warning("Periodic data upload failed: " + e.getMessage());
            }
        };

        scheduler.scheduleAtFixedRate(uploadTask, 0, UPDATE_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
}
package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import pojo.WeatherPojo;
import pojo.WeatherWrapperPojo;
import utils.JsonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

public class AggregationServer {
    private static final Logger logger = LoggerFactory.getLogger(AggregationServer.class);
    private final Gson gson = new Gson();

    private static final int DEFAULT_PORT = 4567;
    private static final int DATA_EXPIRATION_SECONDS = 30;
    private static final int MAX_UPDATES = 20;
    private static final String DATA_FILE = "weatherInfo.json";

    private final int port;
    final Map<String, WeatherPojo> weatherMap;
    private final Map<String, Map<String, WeatherWrapperPojo>> wrapperMap;
    private final Map<String, Long> updateTimeMap;
    //    private final PriorityQueue<WeatherPojo> recentUpdates = new PriorityQueue<>();
    private volatile boolean running;
    private ServerSocket serverSocket;
    private int lamportClock;
    private final CountDownLatch serverStartLatch = new CountDownLatch(1);
    private ExecutorService threadPool;

    public AggregationServer(int port) {
        this.port = port;
        this.weatherMap = new ConcurrentHashMap<>();
        this.wrapperMap = new ConcurrentHashMap<>();
        this.updateTimeMap = new ConcurrentHashMap<>();
        this.running = false;
        this.lamportClock = 0;
        loadDataFromFile();
    }

    /**
     * Loads weather data from the storage file into memory.
     * If the file does not exist, starts with an empty data set.
     */
    private void loadDataFromFile() {
        Path path = Paths.get(DATA_FILE);
        try (InputStream inputStream = Files.newInputStream(path);
             InputStreamReader reader = new InputStreamReader(inputStream)) {
            Type mapType = new TypeToken<Map<String, Map<String, WeatherWrapperPojo>>>() {}.getType();
            Map<String, Map<String, WeatherWrapperPojo>> loadedData = gson.fromJson(reader, mapType);
            if (loadedData != null && !loadedData.isEmpty()) {
                wrapperMap.putAll(loadedData);
                logger.info("Successfully loaded weather data, total records: {}", wrapperMap.size());
            } else {
                logger.info("Invalid or empty weather data file, initializing empty data map.");
            }
            logger.info(JsonHelper.toJson(wrapperMap));
        } catch (IOException e) {
            logger.error("Failed to load weather data file, it may not exist or failed to read.", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        logger.info("Data loaded from file. WeatherPojo map size: {}", weatherMap.size());
    }

    /**
     * Saves the current weather data to the storage file.
     */
    private synchronized void saveDataToFile() {
        Path path = Paths.get(DATA_FILE);
        System.out.println("toAbsolutePath======"+path.toAbsolutePath());
        File file = path.toFile();

        if (file.exists() && file.canWrite()) {
            try (FileWriter writer = new FileWriter(file)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(wrapperMap, writer);
                logger.info("WeatherPojo data file updated at: {}", file.getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            logger.warn("Cannot write to the existing weather data file.");
        }
    }

    /**
     * Starts the server and begins accepting client connections.
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(10000);
        running = true;
        threadPool = Executors.newFixedThreadPool(5);
        serverStartLatch.countDown();
        logger.info("Server started on port {}", port);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::removeExpiredData, 0, 5, TimeUnit.SECONDS);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout(5000);
                threadPool.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (running) {
                    logger.warn("Error accepting client connection: {}", e.getMessage());
                }
            }
        }

        threadPool.shutdown();
        scheduler.shutdown();
    }

    /**
     * Waits for the server to start within the specified timeout.
     */
    public boolean waitForServerStart(long timeout, TimeUnit unit) throws InterruptedException {
        return serverStartLatch.await(timeout, unit);
    }

    /**
     * Stops the server and shuts down all resources.
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.warn("Error closing server socket: {}", e.getMessage());
        }
        if (threadPool != null) {
            threadPool.shutdownNow();
        }
        saveDataToFile();
        logger.info("Server stopped.");
    }

    /**
     * Handles client connections and processes their requests.
     */
    private void handleClient(Socket clientSocket) {
        try (clientSocket;
             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String requestLine = in.readLine();
            if (requestLine != null) {
                String[] requestParts = requestLine.split(" ");
                logger.info("Received request: {}", requestLine);
                if (requestParts.length == 3) {
                    String method = requestParts[0];
                    String path = requestParts[1];

                    if ("GET".equals(method) && path.startsWith("/weatherInfo.json")) {
                        handleGetRequest(out, path);
                    } else if ("PUT".equals(method)) {
                        handlePutRequest(in, out);
                    } else {
                        sendResponse(out, "400 Bad Request", "Invalid request");
                    }
                } else {
                    sendResponse(out, "400 Bad Request", "Invalid request format");
                }
            }
        } catch (IOException e) {
            logger.warn("Error handling client: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error handling client: {}", e.getMessage(), e);
        }
    }

    /**
     * Handles GET requests from clients.
     */
    private void handleGetRequest(PrintWriter out, String path) {
        synchronized (this) {
            lamportClock++;
        }
        removeExpiredData();
        logger.info("Handling GET request. Path: {}", path);

        String stationId = null;
        if (path.contains("?id=")) {
            stationId = path.split("\\?id=")[1];
        }

        if (stationId != null) {
            WeatherPojo data = weatherMap.get(stationId);
            if (data != null) {
                sendResponse(out, "200 OK", gson.toJson(data));
                logger.info("WeatherPojo data available for station: {}. Sending 200 OK with data.", stationId);
            } else {
                sendResponse(out, "404 Not Found", "No weather data available for station: " + stationId);
                logger.info("No weather data available for station: {}. Sending 404 Not Found.", stationId);
            }
        } else {
            logger.info("WeatherPojo map size: {}", weatherMap.size());
            if (weatherMap.isEmpty()) {
                logger.info("No weather data available. Sending 404 Not Found.");
                sendResponse(out, "404 Not Found", null);
            } else {
                logger.info("WeatherPojo data available. Sending 200 OK with data.");
//                List<WeatherPojo> sortedData =  new ArrayList<>(recentUpdates);
                List<WeatherPojo> sortedData = new ArrayList<>(weatherMap.values());
                StringBuilder jsonResponse = new StringBuilder("[");
                for (WeatherPojo data : sortedData) {
                    jsonResponse.append(gson.toJson(data)).append(",");
                }
                if (jsonResponse.charAt(jsonResponse.length() - 1) == ',') {
                    jsonResponse.setLength(jsonResponse.length() - 1);
                }
                jsonResponse.append("]");
                sendResponse(out, "200 OK", jsonResponse.toString());
            }
        }
    }

    /**
     * Handles PUT requests from clients.
     */
    private void handlePutRequest(BufferedReader in, PrintWriter out) throws IOException {
        Map<String, Integer> headers = readHeaders(in);
        int contentLength = headers.getOrDefault("Content-Length", 0);
        int clientClock = headers.getOrDefault("Lamport-Clock", 0);

        String jsonData = readBody(in, contentLength);
        logger.info("Received PUT request with data: {}", jsonData);

        updateLamportClock(clientClock);

        if (jsonData.isEmpty()) {
            sendResponse(out, "204 No Content", null);
            logger.info("No content sent in PUT request. Sending 204 No Content.");
            return;
        }

        processWeatherData(jsonData, out);
    }

    /**
     * Reads headers from the request and extracts Content-Length and Lamport-Clock values.
     */
    private Map<String, Integer> readHeaders(BufferedReader in) throws IOException {
        Map<String, Integer> headers = new HashMap<>();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            if (line.toLowerCase().startsWith("content-length:")) {
                headers.put("Content-Length", Integer.parseInt(line.substring("content-length:".length()).trim()));
            } else if (line.toLowerCase().startsWith("lamport-clock:")) {
                headers.put("Lamport-Clock", Integer.parseInt(line.substring("lamport-clock:".length()).trim()));
            }
        }
        return headers;
    }

    /**
     * Reads the body of the request based on the Content-Length.
     */
    private String readBody(BufferedReader in, int contentLength) throws IOException {
        StringBuilder requestBody = new StringBuilder();
        if (contentLength > 0) {
            char[] body = new char[contentLength];
            int charsRead = in.read(body, 0, contentLength);
            requestBody.append(body, 0, charsRead);
        }
        return requestBody.toString().trim();
    }

    /**
     * Updates the Lamport clock based on the received clock value.
     */
    private void updateLamportClock(int clientClock) {
        synchronized (this) {
            lamportClock = Math.max(lamportClock, clientClock) + 1;
        }
    }

    /**
     * Processes the weather data received in the PUT request.
     */
    private void processWeatherData(String jsonData, PrintWriter out) {
        try {
            WeatherPojo weather = gson.fromJson(jsonData, WeatherPojo.class);
            String stationId = weather.getId();

            boolean isNewContentServer = !wrapperMap.containsKey(stationId);
            boolean isFirstDataEver = weatherMap.isEmpty() && !Files.exists(Paths.get(DATA_FILE));

            synchronized (weatherMap) {
                HashMap<String, WeatherWrapperPojo> inner = new HashMap<>();
                inner.put(weather.getId(), new WeatherWrapperPojo(weather, System.currentTimeMillis()));
                wrapperMap.put(stationId, inner);
                weatherMap.put(stationId, weather);
                updateTimeMap.put(stationId, System.currentTimeMillis());
                if (weatherMap.size() >= 20) {
                    deleteOldestWeatherData();
                }

            }
            saveDataToFile();
            if (isNewContentServer || isFirstDataEver) {
                sendResponse(out, "201 Created", "Data created successfully");
                logger.info("New content server connected or first data ever received. Station: " + stationId);
            } else {
                sendResponse(out, "200 OK", "Data updated successfully");
                logger.info("Weather data updated for station: " + stationId);
            }
        } catch (Exception e) {
            logger.error("Error processing request: {}", e.getMessage());
            sendResponse(out, "500 Internal Server Error", "Error processing request: " + e.getMessage());
        }
    }

    private void deleteOldestWeatherData() {
        synchronized (wrapperMap) {

            // Initialize variables to keep track of the oldest entry
            String oldestOuterKey = null;
            String oldestInnerKey = null;
            WeatherWrapperPojo oldestWrapper = null;

            // Iterate over the map to find the oldest entry
            for (Map.Entry<String, Map<String, WeatherWrapperPojo>> outerEntry : wrapperMap.entrySet()) {
                String outerKey = outerEntry.getKey();
                Map<String, WeatherWrapperPojo> innerMap = outerEntry.getValue();

                for (Map.Entry<String, WeatherWrapperPojo> innerEntry : innerMap.entrySet()) {
                    String innerKey = innerEntry.getKey();
                    WeatherWrapperPojo wrapper = innerEntry.getValue();

                    // Update the oldestWrapper if this wrapper has an older timestamp
                    if (oldestWrapper == null || wrapper.getLastUpdateTime() < oldestWrapper.getLastUpdateTime()) {
                        System.out.println("print info oldestOuterKey = " + outerKey + ", oldestInnerKey =  " + innerKey + ", oldestWrapper = " + wrapper);
                        oldestOuterKey = outerKey;
                        oldestInnerKey = innerKey;
                        oldestWrapper = wrapper;
                    }
                }
            }

            // If we found the oldest entry, remove it from the map
            if (oldestOuterKey != null && oldestInnerKey != null) {
                Map<String, WeatherWrapperPojo> innerMap = wrapperMap.get(oldestOuterKey);
                if (innerMap != null) {
                    weatherMap.remove(oldestInnerKey);
                    wrapperMap.remove(oldestInnerKey);
                    innerMap.remove(oldestInnerKey);
                    // If the inner map becomes empty, remove the outer key
                    if (innerMap.isEmpty()) {
                        wrapperMap.remove(oldestOuterKey);
                    }
                }
            }
        }
    }

    /**
     * Sends an HTTP response to the client.
     */
    private void sendResponse(PrintWriter out, String status, String body) {
        int responseClock;
        synchronized (this) {
            lamportClock++;
            responseClock = lamportClock;
        }
        out.println("HTTP/1.1 " + status);
        out.println("Content-Type: application/json");
        out.println("Lamport-Clock: " + responseClock);
        if (body != null) {
            out.println("Content-Length: " + body.length());
            out.println();
            out.println(body);
        } else {
            out.println("Content-Length: 0");
            out.println();
        }
        out.flush();
        logger.info("Sent response: {},  with Lamport clock: {}", status, responseClock);
    }

    /**
     * Removes expired weather data from the server.
     */
    private void removeExpiredData() {
        long currentTime = System.currentTimeMillis();
        List<String> expiredStations = new ArrayList<>();

        synchronized (updateTimeMap) {
            for (Map.Entry<String, Long> entry : updateTimeMap.entrySet()) {
                if (currentTime - entry.getValue() > DATA_EXPIRATION_SECONDS * 1000) {
                    expiredStations.add(entry.getKey());
                }
            }

            for (String stationId : expiredStations) {
                weatherMap.remove(stationId);
                updateTimeMap.remove(stationId);
                synchronized (wrapperMap) {
                    wrapperMap.remove(stationId);
                }
                logger.info("Removed expired data for station: {}", stationId);
            }

            if (!expiredStations.isEmpty()) {
                saveDataToFile();
            }
        }
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port " + DEFAULT_PORT);
            }
        }

        AggregationServer server = new AggregationServer(port);
        try {
            server.start();
        } catch (IOException e) {
            logger.error("Error starting server: {}", e.getMessage());
        }
    }

    /**
     * Clears all weather data from the server.
     */
    public void clearAllData() {
        weatherMap.clear();
        wrapperMap.clear();
        updateTimeMap.clear();
        logger.info("All data cleared from server.");
    }

    /**
     * Checks if the server is currently running.
     *
     * @return true if the server is running, false otherwise.
     */
    public boolean isRunning() {
        return running;
    }
}
import pojo.WeatherPojo;
import server.AggregationServer;
import utils.CreateWeatherInfoUtil;
import utils.JsonHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class: AggregationServerTest
 * <p>
 * This test class contains unit tests for various functionalities of the AggregationServer,
 * including server startup, request handling, data storage, concurrency handling, and Lamport clock synchronization.
 */
public class AggregationServerTest {

    private static final int TEST_PORT = 4567;
    private static final String TEST_HOST = "localhost";
    private static final int TIMEOUT_SECONDS = 31;
    private static final int MAX_STORED_UPDATES = 20;
    private static final double BASE_TEMPERATURE = 20.0;
    private static final String WEATHER_ENDPOINT = "/weatherInfo.json";
    private static final String STORAGE_FILE = "aggregation_server_data.txt";
    private static final String HTTP_GET = "GET";
    private static final String HTTP_PUT = "PUT";
    private static final String HTTP_VERSION = "HTTP/1.1";
    private static final String CONTENT_TYPE_JSON = "Content-Type: application/json";
    private static final String CONTENT_LENGTH = "Content-Length: ";

    private AggregationServer server;

    /**
     * Sets up the test environment before each test.
     * Initializes the server and starts a thread, waiting for the server to start.
     *
     * @throws IOException          If an I/O error occurs
     * @throws InterruptedException If the thread is interrupted
     * @throws TimeoutException     If the server fails to start within the specified time
     */
    @BeforeEach
    public void setUp() throws IOException, InterruptedException, TimeoutException {
        // Clear the storage file
        Files.write(Paths.get(STORAGE_FILE), new byte[0]);

        server = new AggregationServer(TEST_PORT);
        server.clearAllData(); // Clear data at the start of each test
        new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Wait for the server to start
        boolean serverStarted = server.waitForServerStart(30, TimeUnit.SECONDS);
        if (!serverStarted) {
            throw new TimeoutException("Server did not start within the specified timeout.");
        }

        Thread.sleep(1000); // Add a short delay to ensure the server is fully started
    }

    /**
     * Cleans up the test environment after each test.
     * Stops the server and deletes the storage file to ensure a clean state for the next test.
     *
     * @throws IOException          If an I/O error occurs
     * @throws InterruptedException If the thread is interrupted
     */
    @AfterEach
    public void tearDown() throws IOException, InterruptedException {
        if (server != null) {
            server.stop();
            // Wait for the server to fully stop
            Thread.sleep(2000);
            server = null; // Ensure the server instance is garbage collected
        }
        // Delete the storage file to ensure a clean state for the next test
        Files.deleteIfExists(Paths.get(STORAGE_FILE));
    }

    /**
     * Tests whether the server can start successfully and listen on the specified port.
     * Verifies that the server is running after startup.
     */
    @Test
    public void shouldStartServerSuccessfully() {
        assertTrue(server.isRunning(), "Server should be running after startup.");
    }

    /**
     * Tests whether the server can correctly handle a PUT request, create a storage file, and store data.
     * Sends a PUT request containing weather information and verifies that the server returns a 201 Created response code.
     *
     * @throws IOException           If an I/O error occurs
     * @throws IllegalAccessException If a reflection error occurs
     */
    @Test
    public void shouldHandlePutRequestSuccessfully() throws IOException, IllegalAccessException {
        String jsonData = JsonHelper.toJson(CreateWeatherInfoUtil.createMockWeatherInfo("IDS60901"));
        String putRequest = createPutRequest(jsonData);

        String response = sendRequest(putRequest);
        System.out.println("PUT Response: " + response);
        assertTrue(response.contains(HTTP_VERSION + " 201 Created"),
                "Server should return 201 Created for a successful PUT request. Actual response: " + response);
    }

    /**
     * Tests whether the server can correctly handle subsequent PUT requests and return the correct response codes.
     * Sends multiple PUT requests to the same and different stations and verifies the correctness of the response codes.
     *
     * @throws IOException           If an I/O error occurs
     * @throws IllegalAccessException If a reflection error occurs
     */
    @Test
    public void shouldHandleSubsequentPutRequestsCorrectly() throws IOException, IllegalAccessException {
        String jsonData1 = JsonHelper.toJson(CreateWeatherInfoUtil.createMockWeatherInfo("IDS60901"));
        String jsonData2 = JsonHelper.toJson(CreateWeatherInfoUtil.createMockWeatherInfo("IDS60901"));
        String jsonData3 = JsonHelper.toJson(CreateWeatherInfoUtil.createMockWeatherInfo("IDS60902"));

        String response1 = sendRequest(createPutRequest(jsonData1));
        String response2 = sendRequest(createPutRequest(jsonData2));
        String response3 = sendRequest(createPutRequest(jsonData3));
        System.out.println(response1);
        assertTrue(response1.contains(HTTP_VERSION + " 201 Created"),
                "Server should return 201 Created for the first PUT request");
        assertTrue(response2.contains(HTTP_VERSION + " 200 OK"),
                "Server should return 200 OK for subsequent PUT requests to the same station");
        assertTrue(response3.contains(HTTP_VERSION + " 201 Created"),
                "Server should return 201 Created for a new station");
    }

    /**
     * Tests whether the server can correctly handle a GET request after a PUT request and return the stored data.
     * Verifies the correctness of the server's response code and content.
     *
     * @throws IOException           If an I/O error occurs
     * @throws IllegalAccessException If a reflection error occurs
     */
    @Test
    public void shouldReturnDataOnGetRequestAfterPut() throws IOException, IllegalAccessException {
        String jsonData = JsonHelper.toJson(CreateWeatherInfoUtil.createMockWeatherInfo("IDS60901"));
        String putRequest = createPutRequest(jsonData);
        sendRequest(putRequest);

        String getResponse = sendRequest(createGetRequest());
        System.out.println("GET Response: " + getResponse);
        assertTrue(getResponse.contains(HTTP_VERSION + " 200 OK"),
                "Server should return 200 OK for GET request after PUT");
        assertTrue(getResponse.contains("\"id\":\"IDS60901\""),
                "GET response should contain the PUT data");
    }

    /**
     * Tests whether the server can correctly remove data from inactive content servers after a timeout.
     * Sends a PUT request, waits beyond the timeout, and then sends a GET request to verify data removal.
     *
     * @throws InterruptedException  If the thread is interrupted
     * @throws IOException           If an I/O error occurs
     * @throws IllegalAccessException If a reflection error occurs
     */
    @Test
    public void shouldExpireDataAfterTimeout() throws InterruptedException, IOException, IllegalAccessException {
        String jsonData = JsonHelper.toJson(CreateWeatherInfoUtil.createMockWeatherInfo("IDS60901"));
        String putRequest = createPutRequest(jsonData);
        sendRequest(putRequest);

        // Wait beyond the timeout period
        Thread.sleep(TIMEOUT_SECONDS * 1000);

        String getResponse = sendRequest(createGetRequest());
        assertTrue(getResponse.contains(HTTP_VERSION + " 404 Not Found"),
                "Server should return 404 Not Found after data expiration");
    }

    /**
     * Tests how the server handles a PUT request with no content.
     * Sends a PUT request with empty content and verifies that the server returns a 204 No Content response code.
     *
     * @throws IOException If an I/O error occurs
     */
    @Test
    public void shouldHandlePutRequestWithNoContent() throws IOException {
        String putRequest = "PUT " + WEATHER_ENDPOINT + " " + HTTP_VERSION + "\r\n" +
                CONTENT_TYPE_JSON + "\r\n" +
                CONTENT_LENGTH + "0\r\n\r\n";

        String response = sendRequest(putRequest);
        assertTrue(response.contains(HTTP_VERSION + " 204 No Content"),
                "Server should return 204 No Content when no content is sent. Actual response: " + response);
    }

    /**
     * Tests whether the server can correctly handle multiple simultaneous GET and PUT requests in a high-concurrency environment.
     * Uses a thread pool to simulate concurrent requests and verifies the correctness of all responses.
     *
     * @throws InterruptedException If the thread is interrupted
     * @throws IOException          If an I/O error occurs
     */
    @Test
    public void shouldHandleConcurrentRequestsSuccessfully() throws InterruptedException, IOException {
        int numThreads = 10;
        CountDownLatch latch = new CountDownLatch(numThreads);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    String jsonData = JsonHelper.toJson(CreateWeatherInfoUtil.createMockWeatherInfo("IDS60901"));
                    String putRequest = createPutRequest(jsonData);
                    String response = sendRequest(putRequest);
                    System.out.println("Concurrent PUT response for station " + index + ": " + response);
                    assertTrue(response.contains(HTTP_VERSION + " 200 OK") || response.contains(HTTP_VERSION + " 201 Created"),
                            "All requests should be successful. Actual response: " + response);
                } catch (IOException | IllegalAccessException e) {
                    fail("Exception occurred during concurrent request: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        String getResponse = sendRequest(createGetRequest());
        System.out.println("GET response after concurrent requests: " + getResponse);
        assertTrue(getResponse.contains(HTTP_VERSION + " 200 OK"),
                "GET request after concurrent PUTs should return 200 OK");
    }

    /**
     * Tests how the server handles an invalid request method.
     * Sends a request with an unsupported method and verifies that the server returns a 400 Bad Request response code.
     *
     * @throws IOException If an I/O error occurs
     */
    @Test
    public void shouldReturnBadRequestForInvalidMethod() throws IOException {
        String invalidRequest = "POST /weather.json HTTP/1.1\r\n\r\n";
        String response = sendRequest(invalidRequest);

        System.out.println("Invalid method response: " + response);

        assertTrue(response.contains("HTTP/1.1 400 Bad Request"),
                "Server should return 400 Bad Request for an invalid request method. Actual response: " + response);
    }

    /**
     * Tests how the server handles malformed JSON data in a PUT request.
     * Sends a PUT request with malformed JSON data and verifies that the server returns a 500 Internal Server Error response code.
     *
     * @throws IOException If an I/O error occurs
     */
    @Test
    public void shouldHandleMalformedJsonData() throws IOException {
        String malformedJson = "{\"id\":\"IDS60901\",\"name\":\"Test Station\",\"air_temp\":}";

        String putRequest = createPutRequest(malformedJson);

        String response = sendRequest(putRequest);
        System.out.println("Malformed JSON response: " + response);

        assertTrue(response.contains(HTTP_VERSION + " 500 Internal Server Error"),
                "Server should return 500 Internal Server Error for malformed JSON. Actual response: " + response);
    }

    /**
     * Tests whether the server correctly limits stored data to the most recent 20 updates.
     * Sends more than 20 PUT requests and verifies that the server only retains the most recent 20 data entries.
     *
     * @throws IOException           If an I/O error occurs
     * @throws InterruptedException  If the thread is interrupted
     * @throws IllegalAccessException If a reflection error occurs
     */
    @Test
    public void shouldLimitStoredDataTo20Updates() throws IOException, InterruptedException, IllegalAccessException {
        for (int i = 0; i < 25; i++) {
            WeatherPojo wp = CreateWeatherInfoUtil.createMockWeatherInfo("IDS6090" + i);
            String jsonData = JsonHelper.toJson(wp);

            String putRequest = createPutRequest(jsonData);
            String putResponse = sendRequest(putRequest);
            System.out.println("PUT response for station " + i + ": " + putResponse);
            Thread.sleep(100); // Ensure different timestamps
        }

        String getResponse = sendRequest(createGetRequest());
        System.out.println("GET response: " + getResponse);

        String[] ids = getResponse.split("\"id\":\"IDS6090");
        System.out.println("Number of IDs found: " + (ids.length - 1));

        assertEquals(MAX_STORED_UPDATES, ids.length,
                "There should be 20 weather stations in the response. Actual response: " + getResponse);

        // Check that only the most recent 20 updates are present
        for (int i = 24; i > 5; i--) {
            assertTrue(getResponse.contains("\"id\":\"IDS6090" + i + "\""),
                    "Response should contain the most recent 20 updates. Missing: IDS6090" + i);
        }

        // Check that the oldest 5 updates are not present
        for (int i = 0; i < 5; i++) {
            assertFalse(getResponse.contains("\"id\":\"IDS6090" + i + "\""),
                    "Response should not contain the oldest 5 updates. Found: IDS6090" + i);
        }
    }

    /**
     * Tests whether the server can correctly handle fully formatted weather data.
     * Sends weather data containing all fields and verifies that the server stores and returns the data completely.
     *
     * @throws IOException If an I/O error occurs
     */
    @Test
    public void shouldHandleFullyFormattedWeatherData() throws IOException {
        String jsonStr = "{"
                + "\"id\": \"IDS60901\","
                + "\"name\": \"Adelaide (West Terrace / ngayirdapira)\","
                + "\"state\": \"SA\","
                + "\"time_zone\": \"CST\","
                + "\"lat\": -34.9,"
                + "\"lon\": 138.6,"
                + "\"local_date_time\": \"15/04:00pm\","
                + "\"local_date_time_full\": \"20230715160000\","
                + "\"air_temp\": 13.3,"
                + "\"apparent_t\": 9.5,"
                + "\"cloud\": \"Partly cloudy\","
                + "\"dewpt\": 5.7,"
                + "\"press\": 1023.9,"
                + "\"rel_hum\": 60,"
                + "\"wind_dir\": \"S\","
                + "\"wind_spd_kmh\": 15,"
                + "\"wind_spd_kt\": 8"
                + "}";

        String putRequest = createPutRequest(jsonStr);
        String putResponse = sendRequest(putRequest);
        System.out.println("PUT response for fully formatted data: " + putResponse);

        assertTrue(putResponse.contains(HTTP_VERSION + " 201 Created") || putResponse.contains(HTTP_VERSION + " 200 OK"),
                "Server should return 201 Created or 200 OK for a successful PUT request. Actual response: " + putResponse);

        String getResponse = sendRequest(createGetRequest());
        System.out.println("GET response for fully formatted data: " + getResponse);

        assertTrue(getResponse.contains(HTTP_VERSION + " 200 OK"),
                "Server should return 200 OK for GET request after PUT");
        assertTrue(getResponse.contains("\"id\":\"IDS60901\""),
                "GET response should contain the station ID");
        assertTrue(getResponse.contains("\"name\":\"Adelaide (West Terrace / ngayirdapira)\""),
                "GET response should contain the station name");
        assertTrue(getResponse.contains("\"state\":\"SA\""),
                "GET response should contain the state");
        assertTrue(getResponse.contains("\"time_zone\":\"CST\""),
                "GET response should contain the time zone");
        assertTrue(getResponse.contains("\"lat\":-34.9"),
                "GET response should contain the latitude");
        assertTrue(getResponse.contains("\"lon\":138.6"),
                "GET response should contain the longitude");
        assertTrue(getResponse.contains("\"local_date_time\":\"15/04:00pm\""),
                "GET response should contain the local date time");
        assertTrue(getResponse.contains("\"local_date_time_full\":\"20230715160000\""),
                "GET response should contain the full local date time");
        assertTrue(getResponse.contains("\"air_temp\":13.3"),
                "GET response should contain the air temperature");
        assertTrue(getResponse.contains("\"apparent_t\":9.5"),
                "GET response should contain the apparent temperature");
        assertTrue(getResponse.contains("\"cloud\":\"Partly cloudy\""),
                "GET response should contain the cloud condition");
        assertTrue(getResponse.contains("\"dewpt\":5.7"),
                "GET response should contain the dew point");
        assertTrue(getResponse.contains("\"press\":1023.9"),
                "GET response should contain the pressure");
        assertTrue(getResponse.contains("\"rel_hum\":60"),
                "GET response should contain the relative humidity");
        assertTrue(getResponse.contains("\"wind_dir\":\"S\""),
                "GET response should contain the wind direction");
        assertTrue(getResponse.contains("\"wind_spd_kmh\":15"),
                "GET response should contain the wind speed in km/h");
        assertTrue(getResponse.contains("\"wind_spd_kt\":8"),
                "GET response should contain the wind speed in knots");
    }

    /**
     * Tests whether the Lamport clock increments correctly on each PUT request.
     * Sends multiple PUT requests and verifies that the returned Lamport clock values increment.
     *
     * @throws IOException           If an I/O error occurs
     * @throws IllegalAccessException If a reflection error occurs
     */
    @Test
    public void shouldIncrementLamportClockOnPutRequests() throws IOException, IllegalAccessException {
        String jsonData1 = JsonHelper.toJson(CreateWeatherInfoUtil.createMockWeatherInfo("IDS60901"));
        String jsonData2 = JsonHelper.toJson(CreateWeatherInfoUtil.createMockWeatherInfo("IDS60902"));

        String response1 = sendRequest(createPutRequest(jsonData1));
        int clock1 = extractLamportClock(response1);

        String response2 = sendRequest(createPutRequest(jsonData2));
        int clock2 = extractLamportClock(response2);

        assertTrue(clock2 > clock1, "Lamport clock should increment on each PUT request");
    }

    /**
     * Tests whether the Lamport clock increments correctly on each GET request.
     * Sends multiple GET requests and verifies that the returned Lamport clock values increment.
     *
     * @throws IOException           If an I/O error occurs
     * @throws IllegalAccessException If a reflection error occurs
     */
    @Test
    public void shouldIncrementLamportClockOnGetRequests() throws IOException, IllegalAccessException {
        String jsonData = JsonHelper.toJson(CreateWeatherInfoUtil.createMockWeatherInfo("IDS60901"));
        sendRequest(createPutRequest(jsonData));

        String response1 = sendRequest(createGetRequest());
        int clock1 = extractLamportClock(response1);

        String response2 = sendRequest(createGetRequest());
        int clock2 = extractLamportClock(response2);

        assertTrue(clock2 > clock1, "Lamport clock should increment on each GET request");
    }

    /**
     * Tests whether the Lamport clock increments correctly on local events (PUT and GET).
     * Sends PUT and GET requests and verifies the correctness of the Lamport clock.
     *
     * @throws IOException           If an I/O error occurs
     * @throws IllegalAccessException If a reflection error occurs
     */
    @Test
    public void shouldIncrementLamportClockOnLocalEvents() throws IOException, IllegalAccessException {
        String jsonData = JsonHelper.toJson(CreateWeatherInfoUtil.createMockWeatherInfo("IDS60901"));

        String response1 = sendRequest(createPutRequest(jsonData));
        int clock1 = extractLamportClock(response1);

        String response2 = sendRequest(createGetRequest());
        int clock2 = extractLamportClock(response2);

        assertTrue(clock2 > clock1, "Lamport clock should increment on each local event (PUT and GET)");
    }

    /**
     * Tests whether the server correctly synchronizes its Lamport clock when receiving a higher clock value.
     * Sends a request with a higher Lamport clock value and verifies the server's clock synchronization behavior.
     *
     * @throws IOException           If an I/O error occurs
     * @throws IllegalAccessException If a reflection error occurs
     */
    @Test
    public void shouldSynchronizeLamportClockOnReceivingHigherClock() throws IOException, IllegalAccessException {
        String jsonData1 = JsonHelper.toJson(CreateWeatherInfoUtil.createMockWeatherInfo("IDS60901"));
        String jsonData2 = JsonHelper.toJson(CreateWeatherInfoUtil.createMockWeatherInfo("IDS60901"));

        String response1 = sendRequest(createPutRequest(jsonData1));
        int clock1 = extractLamportClock(response1);

        // Simulate a client with a higher Lamport clock
        int higherClock = clock1 + 10;
        String putRequestWithHigherClock = createPutRequestWithClock(jsonData2, higherClock);
        String response2 = sendRequest(putRequestWithHigherClock);
        int clock2 = extractLamportClock(response2);

        assertTrue(clock2 > higherClock, "Server should update its Lamport clock when receiving a higher clock value.");
        assertEquals(higherClock + 2, clock2,
                "Server should set its clock to max(local, received) + 1, then increment for response.");
    }

    /**
     * Tests whether the Lamport clock values are unique and correct in a concurrent environment.
     * Uses a thread pool to send concurrent requests and verifies the uniqueness of each request's Lamport clock value.
     *
     * @throws InterruptedException If the thread is interrupted
     */
    @Test
    public void shouldMaintainUniqueLamportClockInConcurrency() throws InterruptedException {
        int numThreads = 10;
        CountDownLatch latch = new CountDownLatch(numThreads);
        Set<Integer> clockValues = new HashSet<>();

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    String jsonData = JsonHelper.toJson(CreateWeatherInfoUtil.createMockWeatherInfo("IDS6090" + index));

                    String response = sendRequest(createPutRequest(jsonData));
                    int clock = extractLamportClock(response);
                    synchronized (clockValues) {
                        clockValues.add(clock);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(numThreads, clockValues.size(), "Each request should have a unique Lamport clock value.");
    }

    /**
     * Creates a PUT request with the specified JSON data and Lamport clock value.
     *
     * @param jsonData The JSON data to include in the PUT request
     * @param clock    The Lamport clock value to include in the request header
     * @return The formatted PUT request string
     */
    private String createPutRequestWithClock(String jsonData, int clock) {
        return HTTP_PUT + " " + WEATHER_ENDPOINT + " " + HTTP_VERSION + "\r\n" +
                CONTENT_TYPE_JSON + "\r\n" +
                "Lamport-Clock: " + clock + "\r\n" +
                CONTENT_LENGTH + jsonData.length() + "\r\n\r\n" +
                jsonData;
    }

    /**
     * Sends the specified request to the server and returns the response.
     *
     * @param request The request string to send to the server
     * @return The response string from the server
     * @throws IOException If an I/O error occurs while sending the request or receiving the response
     */
    private String sendRequest(String request) throws IOException {
        try (Socket socket = new Socket(TEST_HOST, TEST_PORT)) {
            socket.setSoTimeout(5000); // 5-second timeout
            try (OutputStream os = socket.getOutputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                os.write(request.getBytes());
                os.flush();

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    response.append(line).append("\r\n");
                }

                if (response.toString().contains(CONTENT_LENGTH)) {
                    int contentLength = Integer.parseInt(response.toString().split(CONTENT_LENGTH)[1].split("\r\n")[0]);
                    char[] body = new char[contentLength];
                    int charsRead = reader.read(body, 0, contentLength);
                    if (charsRead != contentLength) {
                        throw new IOException("Failed to read entire response body.");
                    }
                    response.append(body);
                }

                return response.toString();
            }
        } catch (Exception e) {
            throw new IOException("Error sending request or receiving response: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a GET request string.
     *
     * @return The formatted GET request string
     */
    private String createGetRequest() {
        return HTTP_GET + " " + WEATHER_ENDPOINT + " " + HTTP_VERSION + "\r\n" +
                "Lamport-Clock: " + 0 + "\r\n\r\n";
    }

    /**
     * Creates a PUT request with the specified JSON data.
     *
     * @param jsonData The JSON data to include in the PUT request
     * @return The formatted PUT request string
     */
    private String createPutRequest(String jsonData) {
        return HTTP_PUT + " " + WEATHER_ENDPOINT + " " + HTTP_VERSION + "\r\n" +
                CONTENT_TYPE_JSON + "\r\n" +
                CONTENT_LENGTH + jsonData.length() + "\r\n\r\n" +
                jsonData;
    }

    /**
     * Extracts the Lamport clock value from the server response.
     *
     * @param response The response string from the server
     * @return The Lamport clock value, or -1 if not found
     */
    private int extractLamportClock(String response) {
        String[] lines = response.split("\r\n");
        for (String line : lines) {
            if (line.startsWith("Lamport-Clock:")) {
                return Integer.parseInt(line.split(":")[1].trim());
            }
        }
        return -1; // Return -1 if the Lamport-Clock header is not found
    }
}

import client.QueryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test class for QueryClient.
 * This class contains unit tests for various functionalities of the QueryClient.
 */
public class ClientTest {

    private QueryClient QueryClient;
    private HttpURLConnection mockConnection;
    private ByteArrayOutputStream outputStreamCaptor;

    /**
     * Set up the test environment before each test.
     * Initializes the QueryClient, mock connection, and output stream captors.
     *
     * @throws IOException if an error occurs while setting up the test environment.
     */
    @BeforeEach
    public void setUp() throws IOException {
        QueryClient = new QueryClient();
        mockConnection = Mockito.mock(HttpURLConnection.class);
        outputStreamCaptor = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStreamCaptor));

        ByteArrayOutputStream logCaptor = new ByteArrayOutputStream();
        Handler handler = new StreamHandler(logCaptor, new java.util.logging.SimpleFormatter());
        Logger.getLogger(QueryClient.class.getName()).addHandler(handler);
        Logger.getLogger(QueryClient.class.getName()).setLevel(Level.ALL);
    }

    /**
     * Test the initialization of the Lamport clock.
     * The initial Lamport clock value should be 0.
     */
    @Test
    public void testLamportClockInitialization() {
        assertEquals(0, QueryClient.getLamportClock(), "Initial Lamport clock should be 0");
    }

    /**
     * Test the update of the Lamport clock on receiving a response.
     * The Lamport clock should be updated based on the received clock value.
     *
     * @throws IOException if an error occurs while testing the method.
     */
    @Test
    public void testLamportClockUpdateOnReceive() throws IOException {
        URL mockUrl = new URL("http://example.com/weather.json");
        when(mockConnection.getURL()).thenReturn(mockUrl);
        when(mockConnection.getResponseCode()).thenReturn(200);
        when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream("{}".getBytes()));
        when(mockConnection.getHeaderField("Lamport-Clock")).thenReturn("5");

        QueryClient.sendGetRequest(mockConnection);

        assertTrue(QueryClient.getLamportClock() > 5, "Lamport clock should be updated based on received clock");
    }

    /**
     * Test a GET request that returns no content.
     * Verifies that the appropriate message is printed.
     *
     * @throws Exception if an error occurs while testing the method.
     */
    @Test
    public void testNotFoundResponse() throws Exception {
        when(mockConnection.getResponseCode()).thenReturn(404);

        QueryClient.sendGetRequest(mockConnection);

        String expectedOutput = "Error: Server returned status code 404\n";
        assertEquals(expectedOutput, outputStreamCaptor.toString());
    }

    /**
     * Test a GET request that returns an error response.
     * Verifies that the appropriate error message is printed.
     *
     * @throws Exception if an error occurs while testing the method.
     */
    @Test
    public void testErrorResponse() throws Exception {
        when(mockConnection.getResponseCode()).thenReturn(500);

        QueryClient.sendGetRequest(mockConnection);

        String expectedOutput = "Error: Server returned status code 500\n";
        assertEquals(expectedOutput, outputStreamCaptor.toString());
    }

    /**
     * Test creating a connection with an invalid URL.
     * Verifies that an IOException is thrown.
     */
    @Test
    public void testInvalidServerUrl() {
        assertThrows(IOException.class, () -> {
            QueryClient.createConnection("invalid_url");
        });
    }


    /**
     * Test parsing command line arguments without a station ID.
     * Verifies that the server URL is correctly parsed and the station ID is null.
     */
    @Test
    public void testParseCommandLineArgsWithoutStationId() {
        String[] args = { "http://localhost:4567" };
        QueryClient.ServerConfig config = QueryClient.parseCommandLineArgs(args);

        assertEquals("http://localhost:4567", config.serverUrl);
        assertNull(config.stationId);
    }

    /**
     * Test parsing invalid command line arguments.
     * Verifies that an IllegalArgumentException is thrown.
     */
    @Test
    public void testParseCommandLineArgsInvalidInput() {
        String[] args = {};
        assertThrows(IllegalArgumentException.class, () -> {
            QueryClient.parseCommandLineArgs(args);
        });
    }

    /**
     * Test a GET request with a station ID.
     * Verifies that the response is correctly parsed and printed.
     *
     * @throws Exception if an error occurs while testing the method.
     */
    @Test
    public void testGetRequestWithStationId() throws Exception {
        URL mockUrl = new URL("http://localhost:4567/weather.json?id=IDS60901");
        when(mockConnection.getURL()).thenReturn(mockUrl);
        when(mockConnection.getResponseCode()).thenReturn(200);
        when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(
                "{\"id\":\"IDS60901\",\"name\":\"Test Station\",\"air_temp\":25.0}".getBytes()));

        QueryClient spyClient = spy(QueryClient);
        doReturn(mockConnection).when(spyClient).createConnection(anyString(), anyString());

        spyClient.sendGetRequest(mockConnection);

        String expectedOutput = "id: IDS60901\nname: Test Station\nair_temp: 25.0\n";
        assertEquals(expectedOutput.trim(), outputStreamCaptor.toString().trim());
    }


    /**
     * Test the failure tolerance of the GET client.
     * Verifies that the client retries the GET request and eventually succeeds.
     *
     * @throws Exception if an error occurs while testing the method.
     */
    @Test
    public void testFailureTolerance() throws Exception {
        URL mockUrl = new URL("http://example.com/weather.json");

        HttpURLConnection mockConnection1 = Mockito.mock(HttpURLConnection.class);
        HttpURLConnection mockConnection2 = Mockito.mock(HttpURLConnection.class);
        HttpURLConnection mockConnection3 = Mockito.mock(HttpURLConnection.class);

        when(mockConnection1.getURL()).thenReturn(mockUrl);
        when(mockConnection2.getURL()).thenReturn(mockUrl);
        when(mockConnection3.getURL()).thenReturn(mockUrl);

        when(mockConnection1.getResponseCode()).thenThrow(new IOException("Connection failed"));
        when(mockConnection2.getResponseCode()).thenThrow(new IOException("Connection failed again"));
        when(mockConnection3.getResponseCode()).thenReturn(200);

        when(mockConnection3.getInputStream()).thenReturn(
                new ByteArrayInputStream("{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"air_temp\":23.5}".getBytes()));

        QueryClient spyClient = Mockito.spy(QueryClient);
        Mockito.doReturn(mockConnection2).doReturn(mockConnection3).when(spyClient).createConnection(anyString());

        spyClient.sendGetRequestWithRetry(mockConnection1);

        verify(spyClient, times(2)).createConnection(anyString());
        verify(spyClient, times(3)).sendGetRequest(any(HttpURLConnection.class));

        String expectedOutput = "id: IDS60901\nname: Adelaide\nair_temp: 23.5\n";
        assertTrue(outputStreamCaptor.toString().endsWith(expectedOutput));
    }
}
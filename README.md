
# Weather Data Aggregation and Distribution System

## Overview

This project is a client-server system designed to aggregate and distribute weather data using a RESTful API in Java. It includes the following components:

1. **Aggregation Server:** Aggregates weather data from multiple sources and provides responses to GET and PUT requests.
2. **Content Server:** Handles the processing and storage of weather information.
3. **Query Client:** Simulates client requests to the aggregation server for testing and demonstration purposes.
4. **Unit Tests:** Tests for various functionalities of the system, including handling concurrent requests, malformed data, and Lamport clock synchronization.

## Components

### 1. Aggregation Server

- **File:** `AggregationServer.java`
- **Functionality:** Receives weather data from clients and stores the most recent 20 updates. Handles concurrent GET and PUT requests. Manages Lamport clock synchronization for distributed data consistency.

### 2. Content Server

- **File:** `ContentServer.java`
- **Functionality:** Processes incoming weather data, maintains the storage of data records, and communicates with the aggregation server.

### 3. Query Client

- **File:** `QueryClient.java`
- **Functionality:** Simulates client-side requests to the aggregation server, including sending PUT and GET requests with weather data in JSON format.

### 4. Unit Tests

- **File:** `AggregationServerTest.java`
- **Functionality:** Contains unit tests to verify various aspects of the system, including:
  - Handling of concurrent requests.
  - Response to invalid HTTP methods.
  - Data validation for correctly formatted and malformed JSON.
  - Limitation of stored data to the most recent 20 updates.
  - Correct synchronization and updating of the Lamport clock.

## Usage

1. Compile all Java files:
   ```bash
   javac AggregationServer.java ContentServer.java QueryClient.java AggregationServerTest.java
   ```

2. Run the aggregation server:
   ```bash
   java AggregationServer
   ```

3. Run the content server:
   ```bash
   java ContentServer
   ```

4. Use the query client to send requests:
   ```bash
   java QueryClient
   ```

5. To run the unit tests:
   ```bash
   java AggregationServerTest
   ```

## Features

- **Weather Data Storage:** Stores the latest 20 weather data updates, ensuring old data is automatically removed.
- **Concurrent Request Handling:** Designed to handle multiple simultaneous requests efficiently.
- **Lamport Clock Synchronization:** Uses Lamport clocks for event ordering in a distributed system.
- **Error Handling:** Detects and appropriately handles invalid requests and malformed data.

## Dependencies

- Java SE Development Kit (JDK) 8 or higher.

## Testing

The unit tests included in `AggregationServerTest.java` cover the following scenarios:

1. **Concurrent Requests:** Tests the server's ability to handle multiple concurrent GET and PUT requests.
2. **Invalid Methods:** Checks the server's response to unsupported HTTP methods.
3. **Malformed JSON:** Verifies that the server returns an appropriate error when receiving malformed JSON data.
4. **Data Storage Limitation:** Ensures that the server only retains the most recent 20 weather data entries.
5. **Lamport Clock Synchronization:** Validates that the Lamport clock is correctly incremented and synchronized across PUT and GET requests.
6. **Unique Clock Values:** Tests for uniqueness and correctness of Lamport clock values in a concurrent environment.

## Contributing

Contributions are welcome. Please ensure that any new code is accompanied by appropriate tests in `AggregationServerTest.java`.

## License

This project is licensed under the MIT License. See the LICENSE file for more information.

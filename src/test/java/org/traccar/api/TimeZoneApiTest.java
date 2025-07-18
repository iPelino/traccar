package org.traccar.api;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

public class TimeZoneApiTest {

    private Client client;
    private WebTarget target;
    private static final String BASE_URL = "http://localhost:8082/api"; // Assuming server is running on port 8082
    private static final String TIMEZONES_ENDPOINT = "/timezones";

    @BeforeEach
    public void setUp() {
        client = ClientBuilder.newClient();
        target = client.target(BASE_URL);
    }

    /**
     * Checks if the server is running and the API is accessible.
     * @return true if the server is running and the API is accessible, false otherwise
     */
    private boolean isServerRunning() {
        try {
            Response response = target.path("/").request().get();
            return response.getStatus() != 404;
        } catch (ProcessingException e) {
            System.out.println("[DEBUG_LOG] Server is not running: " + e.getMessage());
            return false;
        }
    }

    @AfterEach
    public void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    public void testGetTimezones() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // Test that the timezones endpoint returns a list of valid IANA timezones
        Response response = target.path(TIMEZONES_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .get();

        assertEquals(200, response.getStatus(), "Response status should be 200 OK");
        List<?> timezones = response.readEntity(List.class);
        assertNotNull(timezones, "Timezones list should not be null");
        assertFalse(timezones.isEmpty(), "Timezones list should not be empty");

        // Check that the list contains common timezones
        assertTrue(timezones.contains("UTC"), "Timezones should include UTC");
        assertTrue(timezones.contains("America/New_York"), "Timezones should include America/New_York");
        assertTrue(timezones.contains("Europe/London"), "Timezones should include Europe/London");

        System.out.println("[DEBUG_LOG] Retrieved " + timezones.size() + " timezones");
    }

    @Test
    public void testTimezonesAccessibility() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // Test that the timezones endpoint is accessible without authentication
        // This is a reference API that should be available to all users
        Response response = target.path(TIMEZONES_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .get();

        assertEquals(200, response.getStatus(), "Response status should be 200 OK");
        System.out.println("[DEBUG_LOG] Timezones API is accessible without authentication");
    }

    @Test
    public void testTimezonesValidity() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // Test that all returned timezones are valid IANA timezones
        Response response = target.path(TIMEZONES_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .get();

        assertEquals(200, response.getStatus(), "Response status should be 200 OK");
        List<String> timezones = response.readEntity(List.class);
        
        // Get all available timezone IDs from Java
        String[] availableIDs = TimeZone.getAvailableIDs();
        
        // Check that each timezone from the API is a valid Java timezone
        for (String timezone : timezones) {
            boolean isValid = false;
            for (String availableID : availableIDs) {
                if (availableID.equals(timezone)) {
                    isValid = true;
                    break;
                }
            }
            assertTrue(isValid, "Timezone " + timezone + " should be a valid IANA timezone");
        }
        
        System.out.println("[DEBUG_LOG] All timezones are valid IANA timezones");
    }
}
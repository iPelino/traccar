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

import static org.junit.jupiter.api.Assertions.*;

public class IndustryApiTest {

    private Client client;
    private WebTarget target;
    private static final String BASE_URL = "http://localhost:8082/api"; // Assuming server is running on port 8082
    private static final String INDUSTRIES_ENDPOINT = "/industries";

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
    public void testGetIndustries() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // Test that the industries endpoint returns a list of industries
        Response response = target.path(INDUSTRIES_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .get();

        assertEquals(200, response.getStatus(), "Response status should be 200 OK");
        List<?> industries = response.readEntity(List.class);
        assertNotNull(industries, "Industries list should not be null");
        assertFalse(industries.isEmpty(), "Industries list should not be empty");

        // Check that each industry has the expected properties
        if (!industries.isEmpty()) {
            Object firstIndustry = industries.get(0);
            assertTrue(firstIndustry instanceof java.util.Map, "Industry should be a map");
            java.util.Map<?, ?> industryMap = (java.util.Map<?, ?>) firstIndustry;
            assertTrue(industryMap.containsKey("id"), "Industry should have an id");
            assertTrue(industryMap.containsKey("name"), "Industry should have a name");
            
            System.out.println("[DEBUG_LOG] First industry: " + industryMap);
        }

        System.out.println("[DEBUG_LOG] Retrieved " + industries.size() + " industries");
    }

    @Test
    public void testIndustriesAccessibility() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // Test that the industries endpoint is accessible without authentication
        // This is a reference API that should be available to all users
        Response response = target.path(INDUSTRIES_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .get();

        assertEquals(200, response.getStatus(), "Response status should be 200 OK");
        System.out.println("[DEBUG_LOG] Industries API is accessible without authentication");
    }
}
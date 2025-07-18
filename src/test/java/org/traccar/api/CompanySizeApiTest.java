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

public class CompanySizeApiTest {

    private Client client;
    private WebTarget target;
    private static final String BASE_URL = "http://localhost:8082/api"; // Assuming server is running on port 8082
    private static final String COMPANY_SIZES_ENDPOINT = "/company-sizes";

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
    public void testGetCompanySizes() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // Test that the company sizes endpoint returns a list of string size ranges
        Response response = target.path(COMPANY_SIZES_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .get();

        assertEquals(200, response.getStatus(), "Response status should be 200 OK");
        List<?> companySizes = response.readEntity(List.class);
        assertNotNull(companySizes, "Company sizes list should not be null");
        assertFalse(companySizes.isEmpty(), "Company sizes list should not be empty");

        // Check that each company size is a string
        if (!companySizes.isEmpty()) {
            Object firstSize = companySizes.get(0);
            assertTrue(firstSize instanceof String, "Company size should be a string");
            String sizeString = (String) firstSize;
            assertTrue(sizeString.contains("employees"), "Company size should contain 'employees'");
            
            System.out.println("[DEBUG_LOG] First company size: " + sizeString);
        }

        System.out.println("[DEBUG_LOG] Retrieved " + companySizes.size() + " company sizes");
    }

    @Test
    public void testCompanySizesAccessibility() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // Test that the company sizes endpoint is accessible without authentication
        // This is a reference API that should be available to all users
        Response response = target.path(COMPANY_SIZES_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .get();

        assertEquals(200, response.getStatus(), "Response status should be 200 OK");
        System.out.println("[DEBUG_LOG] Company sizes API is accessible without authentication");
    }

    @Test
    public void testCompanySizesFormat() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // Test that the company sizes are in the expected format (e.g., "1-10 employees")
        Response response = target.path(COMPANY_SIZES_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .get();

        assertEquals(200, response.getStatus(), "Response status should be 200 OK");
        List<String> companySizes = response.readEntity(List.class);
        
        // Check that the sizes follow the expected pattern
        for (String size : companySizes) {
            assertTrue(size.matches("\\d+(-\\d+)? employees") || size.matches("\\d+\\+ employees"), 
                    "Company size should match the pattern 'X-Y employees' or 'X+ employees'");
        }
        
        System.out.println("[DEBUG_LOG] All company sizes match the expected format");
    }
}
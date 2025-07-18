package org.traccar.api;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.traccar.model.UserRole;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DriverApiTest {

    private Client client;
    private WebTarget target;
    private static final String BASE_URL = "http://localhost:8082/api"; // Assuming server is running on port 8082
    private static final String SESSION_ENDPOINT = "/session";
    private static final String DRIVERS_ENDPOINT = "/drivers";
    private static final String USERS_ENDPOINT = "/users";

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
    public void testDriverCRUD() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // First login as admin to get session
        Response loginResponse = target.path(SESSION_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("email", "admin")
                        .param("password", "admin")));

        assertEquals(200, loginResponse.getStatus(), "Login response status should be 200 OK");
        String sessionCookie = loginResponse.getCookies().get("JSESSIONID").getValue();

        // Create a new driver
        Map<String, Object> driver = new HashMap<>();
        driver.put("name", "Test Driver");
        driver.put("licenseNo", "DL12345");
        driver.put("phone", "+1234567890");

        // If admin has a company ID, use it
        Map<?, ?> session = loginResponse.readEntity(Map.class);
        if (session.get("companyId") != null) {
            driver.put("companyId", session.get("companyId"));
        }

        Response createResponse = target.path(DRIVERS_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .post(Entity.entity(driver, MediaType.APPLICATION_JSON));

        assertEquals(200, createResponse.getStatus(), "Create response status should be 200 OK");
        Map<?, ?> createdDriver = createResponse.readEntity(Map.class);
        assertNotNull(createdDriver, "Created driver should not be null");
        assertNotNull(createdDriver.get("id"), "Created driver should have an ID");
        assertEquals("Test Driver", createdDriver.get("name"), "Driver name should match");
        assertEquals("DL12345", createdDriver.get("licenseNo"), "Driver license should match");

        System.out.println("[DEBUG_LOG] Created driver with ID: " + createdDriver.get("id"));

        // Get the created driver
        Response getResponse = target.path(DRIVERS_ENDPOINT + "/" + createdDriver.get("id"))
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();

        assertEquals(200, getResponse.getStatus(), "Get response status should be 200 OK");
        Map<?, ?> retrievedDriver = getResponse.readEntity(Map.class);
        assertEquals(createdDriver.get("id"), retrievedDriver.get("id"), "Retrieved driver ID should match");
        assertEquals("Test Driver", retrievedDriver.get("name"), "Retrieved driver name should match");

        System.out.println("[DEBUG_LOG] Retrieved driver: " + retrievedDriver);

        // Update the driver
        Map<String, Object> updatedDriver = new HashMap<>();
        // Copy relevant fields from retrieved driver
        updatedDriver.put("id", retrievedDriver.get("id"));
        updatedDriver.put("name", "Updated Driver Name");
        updatedDriver.put("licenseNo", retrievedDriver.get("licenseNo"));
        updatedDriver.put("phone", "+9876543210");
        if (retrievedDriver.containsKey("companyId") && retrievedDriver.get("companyId") != null) {
            updatedDriver.put("companyId", retrievedDriver.get("companyId"));
        }

        Response updateResponse = target.path(DRIVERS_ENDPOINT + "/" + createdDriver.get("id"))
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .put(Entity.entity(updatedDriver, MediaType.APPLICATION_JSON));

        assertEquals(200, updateResponse.getStatus(), "Update response status should be 200 OK");
        Map<?, ?> updatedDriverResponse = updateResponse.readEntity(Map.class);
        assertEquals("Updated Driver Name", updatedDriverResponse.get("name"), "Updated driver name should match");
        assertEquals("+9876543210", updatedDriverResponse.get("phone"), "Updated driver phone should match");

        System.out.println("[DEBUG_LOG] Updated driver: " + updatedDriverResponse);

        // Delete the driver
        Response deleteResponse = target.path(DRIVERS_ENDPOINT + "/" + createdDriver.get("id"))
                .request()
                .cookie("JSESSIONID", sessionCookie)
                .delete();

        assertEquals(204, deleteResponse.getStatus(), "Delete response status should be 204 No Content");

        System.out.println("[DEBUG_LOG] Deleted driver with ID: " + createdDriver.get("id"));

        // Verify the driver is deleted
        Response getDeletedResponse = target.path(DRIVERS_ENDPOINT + "/" + createdDriver.get("id"))
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();

        assertNotEquals(200, getDeletedResponse.getStatus(), "Get deleted driver should not return 200 OK");

        System.out.println("[DEBUG_LOG] Get deleted driver returned status: " + getDeletedResponse.getStatus());
    }

    @Test
    public void testDriverCompanyScoping() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // First login as admin
        Response adminLoginResponse = target.path(SESSION_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("email", "admin")
                        .param("password", "admin")));

        assertEquals(200, adminLoginResponse.getStatus(), "Admin login response status should be 200 OK");
        String adminSessionCookie = adminLoginResponse.getCookies().get("JSESSIONID").getValue();

        // Create a company admin user
        Map<String, Object> adminUser = new HashMap<>();
        adminUser.put("name", "Company Admin");
        adminUser.put("email", "companyadmin@example.com");
        adminUser.put("password", "password");
        adminUser.put("role", UserRole.ADMIN.toString());

        // If we have a company ID, use it
        Map<?, ?> adminSession = adminLoginResponse.readEntity(Map.class);
        if (adminSession.get("companyId") != null) {
            adminUser.put("companyId", adminSession.get("companyId"));
        }

        Response createAdminResponse = target.path(USERS_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", adminSessionCookie)
                .post(Entity.entity(adminUser, MediaType.APPLICATION_JSON));

        assertEquals(200, createAdminResponse.getStatus(), "Create admin response status should be 200 OK");
        Map<?, ?> createdAdmin = createAdminResponse.readEntity(Map.class);

        // Create a driver with the same company ID
        Map<String, Object> driver = new HashMap<>();
        driver.put("name", "Company Driver");
        driver.put("licenseNo", "COMP123");
        driver.put("phone", "+1122334455");

        // If admin has a company ID, use it
        if (adminSession.get("companyId") != null) {
            driver.put("companyId", adminSession.get("companyId"));
        }

        Response createDriverResponse = target.path(DRIVERS_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", adminSessionCookie)
                .post(Entity.entity(driver, MediaType.APPLICATION_JSON));

        assertEquals(200, createDriverResponse.getStatus(), "Create driver response status should be 200 OK");
        Map<?, ?> createdDriver = createDriverResponse.readEntity(Map.class);

        // Login as the company admin
        Response companyAdminLoginResponse = target.path(SESSION_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("email", "companyadmin@example.com")
                        .param("password", "password")));

        assertEquals(200, companyAdminLoginResponse.getStatus(), "Company admin login response status should be 200 OK");
        String companyAdminSessionCookie = companyAdminLoginResponse.getCookies().get("JSESSIONID").getValue();

        // Get all drivers as company admin
        Response driversResponse = target.path(DRIVERS_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", companyAdminSessionCookie)
                .get();

        assertEquals(200, driversResponse.getStatus(), "Drivers response status should be 200 OK");
        List<?> drivers = driversResponse.readEntity(List.class);

        // Verify the company admin can see the driver
        boolean foundDriver = false;
        for (Object d : drivers) {
            Map<?, ?> driverMap = (Map<?, ?>) d;
            if (driverMap.get("id").equals(createdDriver.get("id"))) {
                foundDriver = true;
                break;
            }
        }

        assertTrue(foundDriver, "Company admin should be able to see drivers in the same company");

        System.out.println("[DEBUG_LOG] Company admin can see drivers in the same company");

        // Clean up - delete the driver and admin user
        target.path(DRIVERS_ENDPOINT + "/" + createdDriver.get("id"))
                .request()
                .cookie("JSESSIONID", adminSessionCookie)
                .delete();

        target.path(USERS_ENDPOINT + "/" + createdAdmin.get("id"))
                .request()
                .cookie("JSESSIONID", adminSessionCookie)
                .delete();
    }
}

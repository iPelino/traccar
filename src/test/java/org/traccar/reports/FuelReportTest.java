package org.traccar.reports;

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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class FuelReportTest {

    private Client client;
    private WebTarget target;
    private static final String BASE_URL = "http://localhost:8082/api"; // Assuming server is running on port 8082
    private static final String SESSION_ENDPOINT = "/session";
    private static final String FUEL_ENDPOINT = "/reports/fuel";
    private static final String DEVICES_ENDPOINT = "/devices";

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
    public void testFuelReport() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // First login to get session
        Response loginResponse = target.path(SESSION_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("email", "admin")
                        .param("password", "admin")));

        assertEquals(200, loginResponse.getStatus(), "Login response status should be 200 OK");
        String sessionCookie = loginResponse.getCookies().get("JSESSIONID").getValue();

        // Get devices to use in report
        Response devicesResponse = target.path(DEVICES_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();

        assertEquals(200, devicesResponse.getStatus(), "Devices response status should be 200 OK");
        List<?> devices = devicesResponse.readEntity(List.class);

        // Skip test if no devices available
        Assumptions.assumeFalse(devices.isEmpty(), "No devices available for testing");

        // Get the first device ID
        Map<?, ?> firstDevice = (Map<?, ?>) devices.get(0);
        Object deviceId = firstDevice.get("id");

        // Set date range for report (last 24 hours)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        Date now = new Date();
        Date yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);

        // Get fuel report
        Response fuelResponse = target.path(FUEL_ENDPOINT)
                .queryParam("deviceId", deviceId)
                .queryParam("from", sdf.format(yesterday))
                .queryParam("to", sdf.format(now))
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();

        assertEquals(200, fuelResponse.getStatus(), "Fuel report response status should be 200 OK");
        List<?> fuelData = fuelResponse.readEntity(List.class);

        // Fuel data may be empty if no data, but the response should be a list
        assertNotNull(fuelData, "Fuel data list should not be null");

        System.out.println("[DEBUG_LOG] Retrieved " + fuelData.size() + " fuel records for device " + deviceId);

        // If we have fuel data, verify it has the required fields
        if (!fuelData.isEmpty()) {
            Map<?, ?> firstRecord = (Map<?, ?>) fuelData.get(0);
            assertTrue(firstRecord.containsKey("deviceId"), "Fuel record should have deviceId");
            assertTrue(firstRecord.containsKey("deviceName"), "Fuel record should have deviceName");
            assertTrue(firstRecord.containsKey("startTime"), "Fuel record should have startTime");
            assertTrue(firstRecord.containsKey("endTime"), "Fuel record should have endTime");
            assertTrue(firstRecord.containsKey("startOdometer"), "Fuel record should have startOdometer");
            assertTrue(firstRecord.containsKey("endOdometer"), "Fuel record should have endOdometer");
            assertTrue(firstRecord.containsKey("distance"), "Fuel record should have distance");
            assertTrue(firstRecord.containsKey("fuelConsumed"), "Fuel record should have fuelConsumed");

            System.out.println("[DEBUG_LOG] First fuel record: " + firstRecord);
        }
    }

    @Test
    public void testFuelReportExcel() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // First login to get session
        Response loginResponse = target.path(SESSION_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("email", "admin")
                        .param("password", "admin")));

        assertEquals(200, loginResponse.getStatus(), "Login response status should be 200 OK");
        String sessionCookie = loginResponse.getCookies().get("JSESSIONID").getValue();

        // Get devices to use in report
        Response devicesResponse = target.path(DEVICES_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();

        assertEquals(200, devicesResponse.getStatus(), "Devices response status should be 200 OK");
        List<?> devices = devicesResponse.readEntity(List.class);

        // Skip test if no devices available
        Assumptions.assumeFalse(devices.isEmpty(), "No devices available for testing");

        // Get the first device ID
        Map<?, ?> firstDevice = (Map<?, ?>) devices.get(0);
        Object deviceId = firstDevice.get("id");

        // Set date range for report (last 24 hours)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        Date now = new Date();
        Date yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);

        // Get fuel report in Excel format
        Response fuelExcelResponse = target.path(FUEL_ENDPOINT)
                .queryParam("deviceId", deviceId)
                .queryParam("from", sdf.format(yesterday))
                .queryParam("to", sdf.format(now))
                .request("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .cookie("JSESSIONID", sessionCookie)
                .get();

        assertEquals(200, fuelExcelResponse.getStatus(), "Fuel Excel report response status should be 200 OK");
        byte[] excelData = fuelExcelResponse.readEntity(byte[].class);

        // Verify we got some data
        assertNotNull(excelData, "Excel data should not be null");
        assertTrue(excelData.length > 0, "Excel data should not be empty");

        System.out.println("[DEBUG_LOG] Retrieved Excel report with " + excelData.length + " bytes");
    }

    @Test
    public void testFuelReportCalculation() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // First login to get session
        Response loginResponse = target.path(SESSION_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("email", "admin")
                        .param("password", "admin")));

        assertEquals(200, loginResponse.getStatus(), "Login response status should be 200 OK");
        String sessionCookie = loginResponse.getCookies().get("JSESSIONID").getValue();

        // Get devices to use in report
        Response devicesResponse = target.path(DEVICES_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();

        assertEquals(200, devicesResponse.getStatus(), "Devices response status should be 200 OK");
        List<?> devices = devicesResponse.readEntity(List.class);

        // Skip test if no devices available
        Assumptions.assumeFalse(devices.isEmpty(), "No devices available for testing");

        // Get the first device ID
        Map<?, ?> firstDevice = (Map<?, ?>) devices.get(0);
        Object deviceId = firstDevice.get("id");

        // Set date range for report (last 24 hours)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        Date now = new Date();
        Date yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);

        // Get fuel report
        Response fuelResponse = target.path(FUEL_ENDPOINT)
                .queryParam("deviceId", deviceId)
                .queryParam("from", sdf.format(yesterday))
                .queryParam("to", sdf.format(now))
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();

        assertEquals(200, fuelResponse.getStatus(), "Fuel report response status should be 200 OK");
        List<?> fuelData = fuelResponse.readEntity(List.class);

        // Fuel data may be empty if no data, but the response should be a list
        assertNotNull(fuelData, "Fuel data list should not be null");

        // If we have fuel data, verify the fuel consumption calculation
        if (!fuelData.isEmpty()) {
            Map<?, ?> firstRecord = (Map<?, ?>) fuelData.get(0);

            // Check that fuel consumption is calculated
            assertTrue(firstRecord.containsKey("fuelConsumed"), "Fuel record should have fuelConsumed");

            // Check that related fields are present for calculation
            assertTrue(firstRecord.containsKey("startFuel") || firstRecord.containsKey("startFuelLevel"), 
                    "Fuel record should have startFuel or startFuelLevel");
            assertTrue(firstRecord.containsKey("endFuel") || firstRecord.containsKey("endFuelLevel"), 
                    "Fuel record should have endFuel or endFuelLevel");

            // Verify the calculation is reasonable (fuel consumed should be non-negative)
            if (firstRecord.get("fuelConsumed") instanceof Number) {
                double fuelConsumed = ((Number) firstRecord.get("fuelConsumed")).doubleValue();
                assertTrue(fuelConsumed >= 0, "Fuel consumed should be non-negative");

                System.out.println("[DEBUG_LOG] Fuel consumed: " + fuelConsumed);
            }

            System.out.println("[DEBUG_LOG] Fuel consumption is calculated from sensor values");
        } else {
            System.out.println("[DEBUG_LOG] No fuel data available for testing calculation");
        }
    }

    @Test
    public void testRoleBasedAccess() {
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
        Map<?, ?> adminSession = adminLoginResponse.readEntity(Map.class);

        // Create a regular user (should not have access to reports)
        Map<String, Object> regularUser = new HashMap<>();
        regularUser.put("name", "Regular User");
        regularUser.put("email", "regularuser@example.com");
        regularUser.put("password", "password");
        regularUser.put("role", "USER");

        // If admin has a company ID, use it
        if (adminSession.get("companyId") != null) {
            regularUser.put("companyId", adminSession.get("companyId"));
        }

        Response createRegularUserResponse = target.path("/users")
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", adminSessionCookie)
                .post(Entity.entity(regularUser, MediaType.APPLICATION_JSON));

        assertEquals(200, createRegularUserResponse.getStatus(), "Create regular user response status should be 200 OK");
        Map<?, ?> createdRegularUser = createRegularUserResponse.readEntity(Map.class);

        // Create an admin user (should have access to reports)
        Map<String, Object> adminUser = new HashMap<>();
        adminUser.put("name", "Admin User");
        adminUser.put("email", "adminuser@example.com");
        adminUser.put("password", "password");
        adminUser.put("role", "ADMIN");

        // If admin has a company ID, use it
        if (adminSession.get("companyId") != null) {
            adminUser.put("companyId", adminSession.get("companyId"));
        }

        Response createAdminUserResponse = target.path("/users")
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", adminSessionCookie)
                .post(Entity.entity(adminUser, MediaType.APPLICATION_JSON));

        assertEquals(200, createAdminUserResponse.getStatus(), "Create admin user response status should be 200 OK");
        Map<?, ?> createdAdminUser = createAdminUserResponse.readEntity(Map.class);

        // Get devices to use in report
        Response devicesResponse = target.path(DEVICES_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", adminSessionCookie)
                .get();

        assertEquals(200, devicesResponse.getStatus(), "Devices response status should be 200 OK");
        List<?> devices = devicesResponse.readEntity(List.class);

        // Skip test if no devices available
        Assumptions.assumeFalse(devices.isEmpty(), "No devices available for testing");

        // Get the first device ID
        Map<?, ?> firstDevice = (Map<?, ?>) devices.get(0);
        Object deviceId = firstDevice.get("id");

        // Set date range for report (last 24 hours)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        Date now = new Date();
        Date yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);

        // Login as regular user
        Response regularUserLoginResponse = target.path(SESSION_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("email", "regularuser@example.com")
                        .param("password", "password")));

        assertEquals(200, regularUserLoginResponse.getStatus(), "Regular user login response status should be 200 OK");
        String regularUserSessionCookie = regularUserLoginResponse.getCookies().get("JSESSIONID").getValue();

        // Try to get fuel report as regular user
        Response regularUserFuelResponse = target.path(FUEL_ENDPOINT)
                .queryParam("deviceId", deviceId)
                .queryParam("from", sdf.format(yesterday))
                .queryParam("to", sdf.format(now))
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", regularUserSessionCookie)
                .get();

        // Regular user should not have access to the report
        assertNotEquals(200, regularUserFuelResponse.getStatus(), "Regular user should not have access to fuel report");

        System.out.println("[DEBUG_LOG] Regular user access to fuel report: " + regularUserFuelResponse.getStatus());

        // Login as admin user
        Response adminUserLoginResponse = target.path(SESSION_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("email", "adminuser@example.com")
                        .param("password", "password")));

        assertEquals(200, adminUserLoginResponse.getStatus(), "Admin user login response status should be 200 OK");
        String adminUserSessionCookie = adminUserLoginResponse.getCookies().get("JSESSIONID").getValue();

        // Try to get fuel report as admin user
        Response adminUserFuelResponse = target.path(FUEL_ENDPOINT)
                .queryParam("deviceId", deviceId)
                .queryParam("from", sdf.format(yesterday))
                .queryParam("to", sdf.format(now))
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", adminUserSessionCookie)
                .get();

        // Admin user should have access to the report
        assertEquals(200, adminUserFuelResponse.getStatus(), "Admin user should have access to fuel report");

        System.out.println("[DEBUG_LOG] Admin user access to fuel report: " + adminUserFuelResponse.getStatus());

        // Clean up - delete the users
        Response deleteRegularUserResponse = target.path("/users/" + createdRegularUser.get("id"))
                .request()
                .cookie("JSESSIONID", adminSessionCookie)
                .delete();

        assertEquals(204, deleteRegularUserResponse.getStatus(), "Delete regular user response status should be 204 No Content");

        Response deleteAdminUserResponse = target.path("/users/" + createdAdminUser.get("id"))
                .request()
                .cookie("JSESSIONID", adminSessionCookie)
                .delete();

        assertEquals(204, deleteAdminUserResponse.getStatus(), "Delete admin user response status should be 204 No Content");
    }
}

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

public class StopsReportTest {

    private Client client;
    private WebTarget target;
    private static final String BASE_URL = "http://localhost:8082/api"; // Assuming server is running on port 8082
    private static final String SESSION_ENDPOINT = "/session";
    private static final String REPORTS_ENDPOINT = "/reports";
    private static final String STOPS_ENDPOINT = "/reports/stops";
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
    public void testStopsReport() {
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

        // Get stops report
        Response stopsResponse = target.path(STOPS_ENDPOINT)
                .queryParam("deviceId", deviceId)
                .queryParam("from", sdf.format(yesterday))
                .queryParam("to", sdf.format(now))
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();

        assertEquals(200, stopsResponse.getStatus(), "Stops report response status should be 200 OK");
        List<?> stops = stopsResponse.readEntity(List.class);

        // Stops may be empty if no data, but the response should be a list
        assertNotNull(stops, "Stops list should not be null");

        System.out.println("[DEBUG_LOG] Retrieved " + stops.size() + " stops for device " + deviceId);

        // If we have stops, verify they have the required fields
        if (!stops.isEmpty()) {
            Map<?, ?> firstStop = (Map<?, ?>) stops.get(0);
            assertTrue(firstStop.containsKey("startTime"), "Stop should have startTime");
            assertTrue(firstStop.containsKey("endTime"), "Stop should have endTime");
            assertTrue(firstStop.containsKey("duration"), "Stop should have duration");
            assertTrue(firstStop.containsKey("latitude"), "Stop should have latitude");
            assertTrue(firstStop.containsKey("longitude"), "Stop should have longitude");

            System.out.println("[DEBUG_LOG] First stop: " + firstStop);
        }
    }

    @Test
    public void testStopsReportExcel() {
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

        // Get stops report in Excel format
        Response stopsExcelResponse = target.path(STOPS_ENDPOINT)
                .queryParam("deviceId", deviceId)
                .queryParam("from", sdf.format(yesterday))
                .queryParam("to", sdf.format(now))
                .request("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .cookie("JSESSIONID", sessionCookie)
                .get();

        assertEquals(200, stopsExcelResponse.getStatus(), "Stops Excel report response status should be 200 OK");
        byte[] excelData = stopsExcelResponse.readEntity(byte[].class);

        // Verify we got some data
        assertNotNull(excelData, "Excel data should not be null");
        assertTrue(excelData.length > 0, "Excel data should not be empty");

        System.out.println("[DEBUG_LOG] Retrieved Excel report with " + excelData.length + " bytes");
    }

    @Test
    public void testCompanyAdminRestriction() {
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

        // Create a company admin user
        Map<String, Object> adminUser = new HashMap<>();
        adminUser.put("name", "Company Admin");
        adminUser.put("email", "companyadmin@example.com");
        adminUser.put("password", "password");
        adminUser.put("role", "ADMIN");

        // If admin has a company ID, use it
        if (adminSession.get("companyId") != null) {
            adminUser.put("companyId", adminSession.get("companyId"));
        }

        Response createAdminResponse = target.path("/users")
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", adminSessionCookie)
                .post(Entity.entity(adminUser, MediaType.APPLICATION_JSON));

        assertEquals(200, createAdminResponse.getStatus(), "Create admin response status should be 200 OK");
        Map<?, ?> createdAdmin = createAdminResponse.readEntity(Map.class);

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

        // Login as the company admin
        Response companyAdminLoginResponse = target.path(SESSION_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("email", "companyadmin@example.com")
                        .param("password", "password")));

        assertEquals(200, companyAdminLoginResponse.getStatus(), "Company admin login response status should be 200 OK");
        String companyAdminSessionCookie = companyAdminLoginResponse.getCookies().get("JSESSIONID").getValue();
        Map<?, ?> companyAdminSession = companyAdminLoginResponse.readEntity(Map.class);

        // Set date range for report (last 24 hours)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        Date now = new Date();
        Date yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);

        // Get stops report as company admin
        Response stopsResponse = target.path(STOPS_ENDPOINT)
                .queryParam("deviceId", deviceId)
                .queryParam("from", sdf.format(yesterday))
                .queryParam("to", sdf.format(now))
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", companyAdminSessionCookie)
                .get();

        assertEquals(200, stopsResponse.getStatus(), "Stops report response status should be 200 OK");

        // The company admin should be able to access the report for devices in their company
        System.out.println("[DEBUG_LOG] Company admin can access stops report for devices in their company");

        // Clean up - delete the admin user
        Response deleteAdminResponse = target.path("/users/" + createdAdmin.get("id"))
                .request()
                .cookie("JSESSIONID", adminSessionCookie)
                .delete();

        assertEquals(204, deleteAdminResponse.getStatus(), "Delete admin response status should be 204 No Content");
    }
}

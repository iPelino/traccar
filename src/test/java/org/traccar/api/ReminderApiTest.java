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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ReminderApiTest {

    private Client client;
    private WebTarget target;
    private static final String BASE_URL = "http://localhost:8082/api"; // Assuming server is running on port 8082
    private static final String SESSION_ENDPOINT = "/session";
    private static final String REMINDERS_ENDPOINT = "/reminders";
    private static final String DEVICES_ENDPOINT = "/devices";
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
    public void testReminderCRUD() {
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
        Map<?, ?> session = loginResponse.readEntity(Map.class);

        // Get devices to use for reminders
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
        
        // Create a reminder
        Map<String, Object> reminder = new HashMap<>();
        reminder.put("vehicleId", deviceId);
        reminder.put("type", "Oil Change");
        
        // Set due date to 30 days from now
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 30);
        reminder.put("dueDate", new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime()));
        
        // Set due mileage
        reminder.put("dueMileage", 5000);
        
        // If admin has a company ID, use it
        if (session.get("companyId") != null) {
            reminder.put("companyId", session.get("companyId"));
        }
        
        Response createReminderResponse = target.path(REMINDERS_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .post(Entity.entity(reminder, MediaType.APPLICATION_JSON));
        
        assertEquals(200, createReminderResponse.getStatus(), "Create reminder response status should be 200 OK");
        Map<?, ?> createdReminder = createReminderResponse.readEntity(Map.class);
        assertNotNull(createdReminder.get("id"), "Created reminder should have an ID");
        assertEquals("Oil Change", createdReminder.get("type"), "Reminder type should match");
        
        System.out.println("[DEBUG_LOG] Created reminder with ID: " + createdReminder.get("id"));
        
        // Get the created reminder
        Response getReminderResponse = target.path(REMINDERS_ENDPOINT + "/" + createdReminder.get("id"))
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();
        
        assertEquals(200, getReminderResponse.getStatus(), "Get reminder response status should be 200 OK");
        Map<?, ?> retrievedReminder = getReminderResponse.readEntity(Map.class);
        assertEquals(createdReminder.get("id"), retrievedReminder.get("id"), "Retrieved reminder ID should match");
        
        System.out.println("[DEBUG_LOG] Retrieved reminder: " + retrievedReminder);
        
        // Update the reminder
        Map<String, Object> updatedReminder = new HashMap<>();
        updatedReminder.put("id", createdReminder.get("id"));
        updatedReminder.put("vehicleId", deviceId);
        updatedReminder.put("type", "Tire Rotation");
        updatedReminder.put("dueDate", new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime()));
        updatedReminder.put("dueMileage", 10000);
        
        // If admin has a company ID, use it
        if (session.get("companyId") != null) {
            updatedReminder.put("companyId", session.get("companyId"));
        }
        
        Response updateReminderResponse = target.path(REMINDERS_ENDPOINT + "/" + createdReminder.get("id"))
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .put(Entity.entity(updatedReminder, MediaType.APPLICATION_JSON));
        
        assertEquals(200, updateReminderResponse.getStatus(), "Update reminder response status should be 200 OK");
        Map<?, ?> updatedReminderResponse = updateReminderResponse.readEntity(Map.class);
        assertEquals("Tire Rotation", updatedReminderResponse.get("type"), "Updated reminder type should match");
        
        System.out.println("[DEBUG_LOG] Updated reminder: " + updatedReminderResponse);
        
        // Complete the reminder
        Response completeReminderResponse = target.path(REMINDERS_ENDPOINT + "/" + createdReminder.get("id") + "/complete")
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();
        
        assertEquals(200, completeReminderResponse.getStatus(), "Complete reminder response status should be 200 OK");
        Map<?, ?> completedReminder = completeReminderResponse.readEntity(Map.class);
        assertTrue((Boolean) completedReminder.get("completed"), "Reminder should be marked as completed");
        
        System.out.println("[DEBUG_LOG] Completed reminder: " + completedReminder);
        
        // Delete the reminder
        Response deleteReminderResponse = target.path(REMINDERS_ENDPOINT + "/" + createdReminder.get("id"))
                .request()
                .cookie("JSESSIONID", sessionCookie)
                .delete();
        
        assertEquals(204, deleteReminderResponse.getStatus(), "Delete reminder response status should be 204 No Content");
        
        System.out.println("[DEBUG_LOG] Deleted reminder with ID: " + createdReminder.get("id"));
    }

    @Test
    public void testUpcomingReminders() {
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
        Map<?, ?> session = loginResponse.readEntity(Map.class);

        // Get devices to use for reminders
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
        
        // Create a reminder due in 7 days
        Map<String, Object> reminder = new HashMap<>();
        reminder.put("vehicleId", deviceId);
        reminder.put("type", "Upcoming Service");
        
        // Set due date to 7 days from now
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 7);
        reminder.put("dueDate", new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime()));
        
        // If admin has a company ID, use it
        if (session.get("companyId") != null) {
            reminder.put("companyId", session.get("companyId"));
        }
        
        Response createReminderResponse = target.path(REMINDERS_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .post(Entity.entity(reminder, MediaType.APPLICATION_JSON));
        
        assertEquals(200, createReminderResponse.getStatus(), "Create reminder response status should be 200 OK");
        Map<?, ?> createdReminder = createReminderResponse.readEntity(Map.class);
        
        // Get upcoming reminders (next 10 days)
        Response getUpcomingResponse = target.path(REMINDERS_ENDPOINT + "/upcoming")
                .queryParam("days", 10)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();
        
        assertEquals(200, getUpcomingResponse.getStatus(), "Get upcoming reminders response status should be 200 OK");
        List<?> upcomingReminders = getUpcomingResponse.readEntity(List.class);
        
        // Verify our reminder is in the upcoming list
        boolean foundReminder = false;
        for (Object r : upcomingReminders) {
            Map<?, ?> reminderMap = (Map<?, ?>) r;
            if (reminderMap.get("id").equals(createdReminder.get("id"))) {
                foundReminder = true;
                break;
            }
        }
        
        assertTrue(foundReminder, "Created reminder should be in the upcoming list");
        
        System.out.println("[DEBUG_LOG] Found reminder in upcoming list");
        
        // Clean up - delete the reminder
        Response deleteReminderResponse = target.path(REMINDERS_ENDPOINT + "/" + createdReminder.get("id"))
                .request()
                .cookie("JSESSIONID", sessionCookie)
                .delete();
        
        assertEquals(204, deleteReminderResponse.getStatus(), "Delete reminder response status should be 204 No Content");
    }

    @Test
    public void testAdminReminderAccess() {
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
        
        // Create an admin user
        Map<String, Object> adminUser = new HashMap<>();
        adminUser.put("name", "Reminder Admin");
        adminUser.put("email", "reminderadmin@example.com");
        adminUser.put("password", "password");
        adminUser.put("role", UserRole.ADMIN.toString());
        
        // If admin has a company ID, use it
        if (adminSession.get("companyId") != null) {
            adminUser.put("companyId", adminSession.get("companyId"));
        }
        
        Response createAdminResponse = target.path(USERS_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", adminSessionCookie)
                .post(Entity.entity(adminUser, MediaType.APPLICATION_JSON));
        
        assertEquals(200, createAdminResponse.getStatus(), "Create admin response status should be 200 OK");
        Map<?, ?> createdAdmin = createAdminResponse.readEntity(Map.class);
        
        // Get devices to use for reminders
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
        
        // Create a reminder
        Map<String, Object> reminder = new HashMap<>();
        reminder.put("vehicleId", deviceId);
        reminder.put("type", "Admin Test Reminder");
        
        // Set due date to 14 days from now
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 14);
        reminder.put("dueDate", new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime()));
        
        // If admin has a company ID, use it
        if (adminSession.get("companyId") != null) {
            reminder.put("companyId", adminSession.get("companyId"));
        }
        
        Response createReminderResponse = target.path(REMINDERS_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", adminSessionCookie)
                .post(Entity.entity(reminder, MediaType.APPLICATION_JSON));
        
        assertEquals(200, createReminderResponse.getStatus(), "Create reminder response status should be 200 OK");
        Map<?, ?> createdReminder = createReminderResponse.readEntity(Map.class);
        
        // Login as the admin user
        Response adminUserLoginResponse = target.path(SESSION_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("email", "reminderadmin@example.com")
                        .param("password", "password")));
        
        assertEquals(200, adminUserLoginResponse.getStatus(), "Admin user login response status should be 200 OK");
        String adminUserSessionCookie = adminUserLoginResponse.getCookies().get("JSESSIONID").getValue();
        
        // Get reminders as admin user
        Response getRemindersResponse = target.path(REMINDERS_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", adminUserSessionCookie)
                .get();
        
        assertEquals(200, getRemindersResponse.getStatus(), "Get reminders response status should be 200 OK");
        List<?> remindersList = getRemindersResponse.readEntity(List.class);
        
        // Verify the admin user can see the reminder
        boolean foundReminder = false;
        for (Object r : remindersList) {
            Map<?, ?> reminderMap = (Map<?, ?>) r;
            if (reminderMap.get("id").equals(createdReminder.get("id"))) {
                foundReminder = true;
                break;
            }
        }
        
        assertTrue(foundReminder, "Admin user should be able to see reminders in the same company");
        
        System.out.println("[DEBUG_LOG] Admin user can see reminders in the same company");
        
        // Clean up - delete the reminder and admin user
        target.path(REMINDERS_ENDPOINT + "/" + createdReminder.get("id"))
                .request()
                .cookie("JSESSIONID", adminSessionCookie)
                .delete();
        
        target.path(USERS_ENDPOINT + "/" + createdAdmin.get("id"))
                .request()
                .cookie("JSESSIONID", adminSessionCookie)
                .delete();
    }
}
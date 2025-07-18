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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DriverIncomeApiTest {

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
    public void testDriverIncomeTracking() {
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
        driver.put("name", "Income Test Driver");
        driver.put("licenseNo", "INC12345");
        driver.put("phone", "+1234567890");
        
        // If admin has a company ID, use it
        Map<?, ?> session = loginResponse.readEntity(Map.class);
        if (session.get("companyId") != null) {
            driver.put("companyId", session.get("companyId"));
        }

        Response createDriverResponse = target.path(DRIVERS_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .post(Entity.entity(driver, MediaType.APPLICATION_JSON));

        assertEquals(200, createDriverResponse.getStatus(), "Create driver response status should be 200 OK");
        Map<?, ?> createdDriver = createDriverResponse.readEntity(Map.class);
        
        // Add income for the driver
        Map<String, Object> income = new HashMap<>();
        income.put("amount", 100.0);
        income.put("date", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        
        Response addIncomeResponse = target.path(DRIVERS_ENDPOINT + "/" + createdDriver.get("id") + "/income")
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .post(Entity.entity(income, MediaType.APPLICATION_JSON));
        
        assertEquals(200, addIncomeResponse.getStatus(), "Add income response status should be 200 OK");
        Map<?, ?> addedIncome = addIncomeResponse.readEntity(Map.class);
        assertNotNull(addedIncome.get("id"), "Added income should have an ID");
        assertEquals(100.0, ((Number) addedIncome.get("amount")).doubleValue(), 0.01, "Income amount should match");
        
        System.out.println("[DEBUG_LOG] Added income with ID: " + addedIncome.get("id"));
        
        // Get income for the driver
        Response getIncomeResponse = target.path(DRIVERS_ENDPOINT + "/" + createdDriver.get("id") + "/income")
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();
        
        assertEquals(200, getIncomeResponse.getStatus(), "Get income response status should be 200 OK");
        List<?> incomeList = getIncomeResponse.readEntity(List.class);
        assertFalse(incomeList.isEmpty(), "Income list should not be empty");
        
        System.out.println("[DEBUG_LOG] Retrieved " + incomeList.size() + " income records");
        
        // Get income summary with daily period
        Response getDailySummaryResponse = target.path(DRIVERS_ENDPOINT + "/" + createdDriver.get("id") + "/income/summary")
                .queryParam("period", "daily")
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();
        
        assertEquals(200, getDailySummaryResponse.getStatus(), "Get daily summary response status should be 200 OK");
        List<?> dailySummary = getDailySummaryResponse.readEntity(List.class);
        assertNotNull(dailySummary, "Daily summary should not be null");
        
        System.out.println("[DEBUG_LOG] Retrieved daily summary with " + dailySummary.size() + " records");
        
        // Get income summary with weekly period
        Response getWeeklySummaryResponse = target.path(DRIVERS_ENDPOINT + "/" + createdDriver.get("id") + "/income/summary")
                .queryParam("period", "weekly")
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();
        
        assertEquals(200, getWeeklySummaryResponse.getStatus(), "Get weekly summary response status should be 200 OK");
        List<?> weeklySummary = getWeeklySummaryResponse.readEntity(List.class);
        assertNotNull(weeklySummary, "Weekly summary should not be null");
        
        System.out.println("[DEBUG_LOG] Retrieved weekly summary with " + weeklySummary.size() + " records");
        
        // Get income summary with monthly period
        Response getMonthlySummaryResponse = target.path(DRIVERS_ENDPOINT + "/" + createdDriver.get("id") + "/income/summary")
                .queryParam("period", "monthly")
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();
        
        assertEquals(200, getMonthlySummaryResponse.getStatus(), "Get monthly summary response status should be 200 OK");
        List<?> monthlySummary = getMonthlySummaryResponse.readEntity(List.class);
        assertNotNull(monthlySummary, "Monthly summary should not be null");
        
        System.out.println("[DEBUG_LOG] Retrieved monthly summary with " + monthlySummary.size() + " records");
        
        // Clean up - delete the driver
        Response deleteDriverResponse = target.path(DRIVERS_ENDPOINT + "/" + createdDriver.get("id"))
                .request()
                .cookie("JSESSIONID", sessionCookie)
                .delete();
        
        assertEquals(204, deleteDriverResponse.getStatus(), "Delete driver response status should be 204 No Content");
    }

    @Test
    public void testFinanceUserAccess() {
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
        
        // Create a finance user
        Map<String, Object> financeUser = new HashMap<>();
        financeUser.put("name", "Finance User");
        financeUser.put("email", "finance@example.com");
        financeUser.put("password", "password");
        financeUser.put("role", UserRole.FINANCE_USER.toString());
        
        // If admin has a company ID, use it
        Map<?, ?> adminSession = adminLoginResponse.readEntity(Map.class);
        if (adminSession.get("companyId") != null) {
            financeUser.put("companyId", adminSession.get("companyId"));
        }
        
        Response createFinanceUserResponse = target.path(USERS_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", adminSessionCookie)
                .post(Entity.entity(financeUser, MediaType.APPLICATION_JSON));
        
        assertEquals(200, createFinanceUserResponse.getStatus(), "Create finance user response status should be 200 OK");
        Map<?, ?> createdFinanceUser = createFinanceUserResponse.readEntity(Map.class);
        
        // Create a driver
        Map<String, Object> driver = new HashMap<>();
        driver.put("name", "Finance Test Driver");
        driver.put("licenseNo", "FIN12345");
        driver.put("phone", "+1234567890");
        
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
        
        // Login as finance user
        Response financeLoginResponse = target.path(SESSION_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("email", "finance@example.com")
                        .param("password", "password")));
        
        assertEquals(200, financeLoginResponse.getStatus(), "Finance user login response status should be 200 OK");
        String financeSessionCookie = financeLoginResponse.getCookies().get("JSESSIONID").getValue();
        
        // Add income for the driver as finance user
        Map<String, Object> income = new HashMap<>();
        income.put("amount", 150.0);
        income.put("date", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        
        Response addIncomeResponse = target.path(DRIVERS_ENDPOINT + "/" + createdDriver.get("id") + "/income")
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", financeSessionCookie)
                .post(Entity.entity(income, MediaType.APPLICATION_JSON));
        
        assertEquals(200, addIncomeResponse.getStatus(), "Finance user should be able to add income");
        
        System.out.println("[DEBUG_LOG] Finance user can add income");
        
        // Clean up - delete the driver and finance user
        target.path(DRIVERS_ENDPOINT + "/" + createdDriver.get("id"))
                .request()
                .cookie("JSESSIONID", adminSessionCookie)
                .delete();
        
        target.path(USERS_ENDPOINT + "/" + createdFinanceUser.get("id"))
                .request()
                .cookie("JSESSIONID", adminSessionCookie)
                .delete();
    }
}
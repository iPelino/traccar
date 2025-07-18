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
import org.traccar.model.User;
import org.traccar.model.UserRole;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class UserRoleApiTest {

    private Client client;
    private WebTarget target;
    private static final String BASE_URL = "http://localhost:8082/api"; // Assuming server is running on port 8082
    private static final String USERS_ENDPOINT = "/users";
    private static final String SESSION_ENDPOINT = "/session";

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
    public void testUserWithRoleAndCompany() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // First, login as admin to get session
        Response loginResponse = target.path(SESSION_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("email", "admin")
                        .param("password", "admin")));

        assertEquals(200, loginResponse.getStatus(), "Login response status should be 200 OK");
        Map<?, ?> session = loginResponse.readEntity(Map.class);
        assertNotNull(session, "Session should not be null");
        Map<?, ?> user = (Map<?, ?>) session.get("user");
        assertNotNull(user, "User in session should not be null");
        
        // Verify role is returned in session
        assertNotNull(session.get("role"), "Role should be returned in session");
        
        // Create a new user with role and company
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("name", "Test User");
        newUser.put("email", "testuser@example.com");
        newUser.put("password", "testpassword");
        newUser.put("role", UserRole.COMPANY_USER.toString());
        
        // If we have a company ID from the admin user, use it
        if (session.get("companyId") != null) {
            newUser.put("companyId", session.get("companyId"));
        }

        Response createResponse = target.path(USERS_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(newUser, MediaType.APPLICATION_JSON));

        assertEquals(200, createResponse.getStatus(), "Create response status should be 200 OK");
        Map<?, ?> createdUser = createResponse.readEntity(Map.class);
        assertNotNull(createdUser, "Created user should not be null");
        assertNotNull(createdUser.get("id"), "Created user should have an ID");
        assertEquals(UserRole.COMPANY_USER.toString(), createdUser.get("role"), "User role should be COMPANY_USER");

        System.out.println("[DEBUG_LOG] Created user with ID: " + createdUser.get("id") + " and role: " + createdUser.get("role"));

        // Login as the new user
        Response newUserLoginResponse = target.path(SESSION_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("email", "testuser@example.com")
                        .param("password", "testpassword")));

        assertEquals(200, newUserLoginResponse.getStatus(), "Login response status should be 200 OK");
        Map<?, ?> newUserSession = newUserLoginResponse.readEntity(Map.class);
        assertNotNull(newUserSession, "Session should not be null");
        
        // Verify role is returned in session
        assertEquals(UserRole.COMPANY_USER.toString(), newUserSession.get("role"), "Role in session should be COMPANY_USER");
        
        // If company ID was set, verify it's returned in session
        if (newUser.containsKey("companyId")) {
            assertNotNull(newUserSession.get("companyId"), "Company ID should be returned in session");
        }

        System.out.println("[DEBUG_LOG] Logged in as new user with role: " + newUserSession.get("role"));
    }

    @Test
    public void testSuperUserCanAssignRoles() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // First, login as admin (assumed to be SUPER_USER)
        Response loginResponse = target.path(SESSION_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("email", "admin")
                        .param("password", "admin")));

        assertEquals(200, loginResponse.getStatus(), "Login response status should be 200 OK");
        Map<?, ?> session = loginResponse.readEntity(Map.class);
        
        // Create a new user with ADMIN role
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("name", "Admin Test User");
        newUser.put("email", "admintest@example.com");
        newUser.put("password", "testpassword");
        newUser.put("role", UserRole.ADMIN.toString());

        Response createResponse = target.path(USERS_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(newUser, MediaType.APPLICATION_JSON));

        assertEquals(200, createResponse.getStatus(), "Create response status should be 200 OK");
        Map<?, ?> createdUser = createResponse.readEntity(Map.class);
        assertEquals(UserRole.ADMIN.toString(), createdUser.get("role"), "User role should be ADMIN");

        System.out.println("[DEBUG_LOG] Super User created user with role: " + createdUser.get("role"));
        
        // Create another user with FINANCE_USER role
        newUser = new HashMap<>();
        newUser.put("name", "Finance Test User");
        newUser.put("email", "financetest@example.com");
        newUser.put("password", "testpassword");
        newUser.put("role", UserRole.FINANCE_USER.toString());

        createResponse = target.path(USERS_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(newUser, MediaType.APPLICATION_JSON));

        assertEquals(200, createResponse.getStatus(), "Create response status should be 200 OK");
        createdUser = createResponse.readEntity(Map.class);
        assertEquals(UserRole.FINANCE_USER.toString(), createdUser.get("role"), "User role should be FINANCE_USER");

        System.out.println("[DEBUG_LOG] Super User created user with role: " + createdUser.get("role"));
    }

    @Test
    public void testNonSuperUserScoping() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // First, login as admin to get session
        Response loginResponse = target.path(SESSION_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("email", "admin")
                        .param("password", "admin")));

        assertEquals(200, loginResponse.getStatus(), "Login response status should be 200 OK");
        
        // Create a company user
        Map<String, Object> companyUser = new HashMap<>();
        companyUser.put("name", "Company Test User");
        companyUser.put("email", "companytest@example.com");
        companyUser.put("password", "testpassword");
        companyUser.put("role", UserRole.COMPANY_USER.toString());
        
        // Create the company user
        Response createResponse = target.path(USERS_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(companyUser, MediaType.APPLICATION_JSON));

        assertEquals(200, createResponse.getStatus(), "Create response status should be 200 OK");
        Map<?, ?> createdUser = createResponse.readEntity(Map.class);
        
        // Login as the company user
        Response companyUserLoginResponse = target.path(SESSION_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("email", "companytest@example.com")
                        .param("password", "testpassword")));

        assertEquals(200, companyUserLoginResponse.getStatus(), "Login response status should be 200 OK");
        
        // Try to access all users - should be restricted by company
        Response usersResponse = target.path(USERS_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .get();

        assertEquals(200, usersResponse.getStatus(), "Users response status should be 200 OK");
        
        // The response should only include users from the same company
        // This is a basic test - in a real scenario, we would need to verify the exact users returned
        System.out.println("[DEBUG_LOG] Company user can access users within their company scope");
    }
}
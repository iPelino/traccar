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

public class FinanceApiTest {

    private Client client;
    private WebTarget target;
    private static final String BASE_URL = "http://localhost:8082/api"; // Assuming server is running on port 8082
    private static final String SESSION_ENDPOINT = "/session";
    private static final String FINANCE_ENDPOINT = "/finance";
    private static final String DRIVERS_ENDPOINT = "/drivers";
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
    public void testDriverPaymentsAPI() {
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

        // Create a driver
        Map<String, Object> driver = new HashMap<>();
        driver.put("name", "Payment Test Driver");
        driver.put("licenseNo", "PAY12345");
        driver.put("phone", "+1234567890");
        
        // If admin has a company ID, use it
        if (session.get("companyId") != null) {
            driver.put("companyId", session.get("companyId"));
        }

        Response createDriverResponse = target.path(DRIVERS_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .post(Entity.entity(driver, MediaType.APPLICATION_JSON));

        assertEquals(200, createDriverResponse.getStatus(), "Create driver response status should be 200 OK");
        Map<?, ?> createdDriver = createDriverResponse.readEntity(Map.class);
        
        // Add payment for the driver
        Map<String, Object> payment = new HashMap<>();
        payment.put("driverId", createdDriver.get("id"));
        payment.put("amount", 500.0);
        payment.put("method", "Bank Transfer");
        payment.put("date", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        
        Response addPaymentResponse = target.path(FINANCE_ENDPOINT + "/payments")
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .post(Entity.entity(payment, MediaType.APPLICATION_JSON));
        
        assertEquals(200, addPaymentResponse.getStatus(), "Add payment response status should be 200 OK");
        Map<?, ?> addedPayment = addPaymentResponse.readEntity(Map.class);
        assertNotNull(addedPayment.get("id"), "Added payment should have an ID");
        assertEquals(500.0, ((Number) addedPayment.get("amount")).doubleValue(), 0.01, "Payment amount should match");
        
        System.out.println("[DEBUG_LOG] Added payment with ID: " + addedPayment.get("id"));
        
        // Get payments for the driver
        Response getPaymentsResponse = target.path(FINANCE_ENDPOINT + "/payments")
                .queryParam("driverId", createdDriver.get("id"))
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();
        
        assertEquals(200, getPaymentsResponse.getStatus(), "Get payments response status should be 200 OK");
        List<?> paymentsList = getPaymentsResponse.readEntity(List.class);
        assertFalse(paymentsList.isEmpty(), "Payments list should not be empty");
        
        System.out.println("[DEBUG_LOG] Retrieved " + paymentsList.size() + " payments");
        
        // Delete the payment
        Response deletePaymentResponse = target.path(FINANCE_ENDPOINT + "/payments/" + addedPayment.get("id"))
                .request()
                .cookie("JSESSIONID", sessionCookie)
                .delete();
        
        assertEquals(204, deletePaymentResponse.getStatus(), "Delete payment response status should be 204 No Content");
        
        System.out.println("[DEBUG_LOG] Deleted payment with ID: " + addedPayment.get("id"));
        
        // Clean up - delete the driver
        Response deleteDriverResponse = target.path(DRIVERS_ENDPOINT + "/" + createdDriver.get("id"))
                .request()
                .cookie("JSESSIONID", sessionCookie)
                .delete();
        
        assertEquals(204, deleteDriverResponse.getStatus(), "Delete driver response status should be 204 No Content");
    }

    @Test
    public void testVehicleExpensesAPI() {
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

        // Get devices to use for expenses
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
        
        // Add expense for the vehicle
        Map<String, Object> expense = new HashMap<>();
        expense.put("vehicleId", deviceId);
        expense.put("amount", 200.0);
        expense.put("type", "Fuel");
        expense.put("date", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        
        // If admin has a company ID, use it
        if (session.get("companyId") != null) {
            expense.put("companyId", session.get("companyId"));
        }
        
        Response addExpenseResponse = target.path(FINANCE_ENDPOINT + "/expenses")
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .post(Entity.entity(expense, MediaType.APPLICATION_JSON));
        
        assertEquals(200, addExpenseResponse.getStatus(), "Add expense response status should be 200 OK");
        Map<?, ?> addedExpense = addExpenseResponse.readEntity(Map.class);
        assertNotNull(addedExpense.get("id"), "Added expense should have an ID");
        assertEquals(200.0, ((Number) addedExpense.get("amount")).doubleValue(), 0.01, "Expense amount should match");
        
        System.out.println("[DEBUG_LOG] Added expense with ID: " + addedExpense.get("id"));
        
        // Get expenses for the vehicle
        Response getExpensesResponse = target.path(FINANCE_ENDPOINT + "/expenses")
                .queryParam("vehicleId", deviceId)
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();
        
        assertEquals(200, getExpensesResponse.getStatus(), "Get expenses response status should be 200 OK");
        List<?> expensesList = getExpensesResponse.readEntity(List.class);
        assertFalse(expensesList.isEmpty(), "Expenses list should not be empty");
        
        System.out.println("[DEBUG_LOG] Retrieved " + expensesList.size() + " expenses");
        
        // Test filtering by date
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -7);
        Date fromDate = cal.getTime();
        Date toDate = new Date();
        
        Response getFilteredExpensesResponse = target.path(FINANCE_ENDPOINT + "/expenses")
                .queryParam("vehicleId", deviceId)
                .queryParam("from", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(fromDate))
                .queryParam("to", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(toDate))
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", sessionCookie)
                .get();
        
        assertEquals(200, getFilteredExpensesResponse.getStatus(), "Get filtered expenses response status should be 200 OK");
        List<?> filteredExpensesList = getFilteredExpensesResponse.readEntity(List.class);
        
        System.out.println("[DEBUG_LOG] Retrieved " + filteredExpensesList.size() + " filtered expenses");
        
        // Delete the expense
        Response deleteExpenseResponse = target.path(FINANCE_ENDPOINT + "/expenses/" + addedExpense.get("id"))
                .request()
                .cookie("JSESSIONID", sessionCookie)
                .delete();
        
        assertEquals(204, deleteExpenseResponse.getStatus(), "Delete expense response status should be 204 No Content");
        
        System.out.println("[DEBUG_LOG] Deleted expense with ID: " + addedExpense.get("id"));
    }

    @Test
    public void testFinanceUserCompanyScoping() {
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
        
        // Create a finance user
        Map<String, Object> financeUser = new HashMap<>();
        financeUser.put("name", "Finance Test User");
        financeUser.put("email", "financetest@example.com");
        financeUser.put("password", "password");
        financeUser.put("role", UserRole.FINANCE_USER.toString());
        
        // If admin has a company ID, use it
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
        driver.put("name", "Finance Scope Driver");
        driver.put("licenseNo", "FSD12345");
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
        
        // Add payment for the driver
        Map<String, Object> payment = new HashMap<>();
        payment.put("driverId", createdDriver.get("id"));
        payment.put("amount", 300.0);
        payment.put("method", "Cash");
        payment.put("date", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        
        Response addPaymentResponse = target.path(FINANCE_ENDPOINT + "/payments")
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", adminSessionCookie)
                .post(Entity.entity(payment, MediaType.APPLICATION_JSON));
        
        assertEquals(200, addPaymentResponse.getStatus(), "Add payment response status should be 200 OK");
        Map<?, ?> addedPayment = addPaymentResponse.readEntity(Map.class);
        
        // Login as finance user
        Response financeLoginResponse = target.path(SESSION_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(new jakarta.ws.rs.core.Form()
                        .param("email", "financetest@example.com")
                        .param("password", "password")));
        
        assertEquals(200, financeLoginResponse.getStatus(), "Finance user login response status should be 200 OK");
        String financeSessionCookie = financeLoginResponse.getCookies().get("JSESSIONID").getValue();
        
        // Get payments as finance user
        Response getPaymentsResponse = target.path(FINANCE_ENDPOINT + "/payments")
                .request(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", financeSessionCookie)
                .get();
        
        assertEquals(200, getPaymentsResponse.getStatus(), "Get payments response status should be 200 OK");
        List<?> paymentsList = getPaymentsResponse.readEntity(List.class);
        
        // Verify the finance user can see the payment
        boolean foundPayment = false;
        for (Object p : paymentsList) {
            Map<?, ?> paymentMap = (Map<?, ?>) p;
            if (paymentMap.get("id").equals(addedPayment.get("id"))) {
                foundPayment = true;
                break;
            }
        }
        
        assertTrue(foundPayment, "Finance user should be able to see payments in the same company");
        
        System.out.println("[DEBUG_LOG] Finance user can see payments in the same company");
        
        // Clean up - delete the payment, driver, and finance user
        target.path(FINANCE_ENDPOINT + "/payments/" + addedPayment.get("id"))
                .request()
                .cookie("JSESSIONID", adminSessionCookie)
                .delete();
        
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
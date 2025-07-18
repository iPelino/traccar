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
import org.traccar.model.Company;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CompanyApiTest {

    private Client client;
    private WebTarget target;
    private static final String BASE_URL = "http://localhost:8082/api"; // Assuming server is running on port 8082
    private static final String COMPANIES_ENDPOINT = "/companies";

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
    public void testGetCompanies() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        Response response = target.path(COMPANIES_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .get();

        assertEquals(200, response.getStatus(), "Response status should be 200 OK");
        List<?> companies = response.readEntity(List.class);
        assertNotNull(companies, "Companies list should not be null");

        System.out.println("[DEBUG_LOG] Retrieved " + companies.size() + " companies");
    }

    @Test
    public void testCreateAndGetCompany() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // Create a new company
        Company company = new Company();
        company.setCompanyName("Test Company");
        company.setRegistrationNumber("REG123456");
        company.setIndustryId(1);
        company.setCompanySize("Medium");
        company.setBusinessAddress("123 Test Street");
        company.setPhoneNumber("+1234567890");
        company.setCompanyEmail("test@testcompany.com");
        company.setWebsite("https://testcompany.com");
        company.setTimeZone("UTC");

        // POST request to create the company
        Response createResponse = target.path(COMPANIES_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(company, MediaType.APPLICATION_JSON));

        assertEquals(200, createResponse.getStatus(), "Create response status should be 200 OK");
        Company createdCompany = createResponse.readEntity(Company.class);
        assertNotNull(createdCompany, "Created company should not be null");
        assertNotNull(createdCompany.getId(), "Created company should have an ID");

        System.out.println("[DEBUG_LOG] Created company with ID: " + createdCompany.getId());

        // GET request to retrieve the created company
        Response getResponse = target.path(COMPANIES_ENDPOINT + "/" + createdCompany.getId())
                .request(MediaType.APPLICATION_JSON)
                .get();

        assertEquals(200, getResponse.getStatus(), "Get response status should be 200 OK");
        Company retrievedCompany = getResponse.readEntity(Company.class);
        assertNotNull(retrievedCompany, "Retrieved company should not be null");
        assertEquals(createdCompany.getId(), retrievedCompany.getId(), "Retrieved company should have the same ID");
        assertEquals(company.getCompanyName(), retrievedCompany.getCompanyName(), "Company name should match");

        System.out.println("[DEBUG_LOG] Retrieved company with name: " + retrievedCompany.getCompanyName());
    }

    @Test
    public void testUpdateCompany() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // First create a company
        Company company = new Company();
        company.setCompanyName("Update Test Company");
        company.setRegistrationNumber("REG-UPDATE-123");
        company.setIndustryId(2);
        company.setCompanySize("Small");
        company.setBusinessAddress("456 Update Street");
        company.setPhoneNumber("+9876543210");
        company.setCompanyEmail("update@testcompany.com");
        company.setWebsite("https://update-testcompany.com");
        company.setTimeZone("GMT");

        Response createResponse = target.path(COMPANIES_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(company, MediaType.APPLICATION_JSON));

        assertEquals(200, createResponse.getStatus(), "Create response status should be 200 OK");
        Company createdCompany = createResponse.readEntity(Company.class);

        System.out.println("[DEBUG_LOG] Created company for update test with ID: " + createdCompany.getId());

        // Update the company
        createdCompany.setCompanyName("Updated Company Name");
        createdCompany.setPhoneNumber("+1122334455");

        Response updateResponse = target.path(COMPANIES_ENDPOINT + "/" + createdCompany.getId())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.entity(createdCompany, MediaType.APPLICATION_JSON));

        assertEquals(200, updateResponse.getStatus(), "Update response status should be 200 OK");
        Company updatedCompany = updateResponse.readEntity(Company.class);
        assertNotNull(updatedCompany, "Updated company should not be null");
        assertEquals("Updated Company Name", updatedCompany.getCompanyName(), "Company name should be updated");
        assertEquals("+1122334455", updatedCompany.getPhoneNumber(), "Phone number should be updated");

        System.out.println("[DEBUG_LOG] Updated company name to: " + updatedCompany.getCompanyName());
    }

    @Test
    public void testDeleteCompany() {
        // Skip test if server is not running
        Assumptions.assumeTrue(isServerRunning(), "Server is not running or API endpoint is not accessible");

        // First create a company
        Company company = new Company();
        company.setCompanyName("Delete Test Company");
        company.setRegistrationNumber("REG-DELETE-123");
        company.setIndustryId(3);

        Response createResponse = target.path(COMPANIES_ENDPOINT)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(company, MediaType.APPLICATION_JSON));

        assertEquals(200, createResponse.getStatus(), "Create response status should be 200 OK");
        Company createdCompany = createResponse.readEntity(Company.class);

        System.out.println("[DEBUG_LOG] Created company for delete test with ID: " + createdCompany.getId());

        // Delete the company
        Response deleteResponse = target.path(COMPANIES_ENDPOINT + "/" + createdCompany.getId())
                .request()
                .delete();

        assertEquals(204, deleteResponse.getStatus(), "Delete response status should be 204 No Content");

        System.out.println("[DEBUG_LOG] Deleted company with ID: " + createdCompany.getId());

        // Try to get the deleted company
        Response getResponse = target.path(COMPANIES_ENDPOINT + "/" + createdCompany.getId())
                .request(MediaType.APPLICATION_JSON)
                .get();

        assertNotEquals(200, getResponse.getStatus(), "Get response status should not be 200 OK for deleted company");

        System.out.println("[DEBUG_LOG] Get deleted company returned status: " + getResponse.getStatus());
    }
}

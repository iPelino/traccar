/*
 * Copyright 2024 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.api.resource;

import org.traccar.api.ExtendedObjectResource;
import org.traccar.model.Company;
import org.traccar.model.User;
import org.traccar.model.UserRole;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Pattern;

@Path("companies")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CompanyResource extends ExtendedObjectResource<Company> {

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN =
        Pattern.compile("^\\+?[0-9]{10,15}$");
    private static final int MAX_LOGO_SIZE = 2 * 1024 * 1024; // 2MB

    public CompanyResource() {
        super(Company.class, "companyName");
    }

    private void validateCompany(Company company, Map<String, String> errors) {
        // Validate required fields
        if (company.getCompanyName() == null || company.getCompanyName().trim().isEmpty()) {
            errors.put("companyName", "Company name is required");
        }

        if (company.getBusinessAddress() == null || company.getBusinessAddress().trim().isEmpty()) {
            errors.put("businessAddress", "Business address is required");
        }

        if (company.getIndustryId() <= 0) {
            errors.put("industryId", "Industry is required");
        }

        if (company.getTimeZone() == null || company.getTimeZone().trim().isEmpty()) {
            errors.put("timeZone", "Time zone is required");
        }

        // Validate email format
        if (company.getCompanyEmail() == null || company.getCompanyEmail().trim().isEmpty()) {
            errors.put("companyEmail", "Company email is required");
        } else if (!EMAIL_PATTERN.matcher(company.getCompanyEmail()).matches()) {
            errors.put("companyEmail", "Please enter a valid email address");
        }

        // Validate phone format
        if (company.getPhoneNumber() == null || company.getPhoneNumber().trim().isEmpty()) {
            errors.put("phoneNumber", "Phone number is required");
        } else if (!PHONE_PATTERN.matcher(company.getPhoneNumber()).matches()) {
            errors.put("phoneNumber", "Please enter a valid phone number");
        }
    }

    @GET
    @Override
    public Collection<Company> get(
            @QueryParam("all") boolean all, @QueryParam("userId") long userId,
            @QueryParam("groupId") long groupId, @QueryParam("deviceId") long deviceId) throws StorageException {

        User user = permissionsService.getUser(getUserId());

        // If user is not a SUPER_USER, filter companies based on role
        if (user.getRole() != UserRole.SUPER_USER) {
            var conditions = new LinkedList<Condition>();

            // Admin, Company User, and Finance User can only see their own company
            if (user.getCompanyId() > 0) {
                conditions.add(new Condition.Equals("id", user.getCompanyId()));
            } else {
                // User without company can't see any companies
                return new LinkedList<>();
            }

            return storage.getObjects(baseClass, new Request(
                    new Columns.All(), Condition.merge(conditions)));
        }

        // For SUPER_USER, use the default behavior
        return super.get(all, userId, groupId, deviceId);
    }

    @POST
    @Path("create")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createCompany(Map<String, Object> data) throws Exception {
        User user = permissionsService.getUser(getUserId());

        // Only SUPER_USER or ADMIN can create companies
        if (user.getRole() != UserRole.SUPER_USER && user.getRole() != UserRole.ADMIN) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Administrator access required"))
                    .build();
        }

        Company company = new Company();
        Map<String, String> errors = new HashMap<>();

        // Set company properties from the request data
        if (data.containsKey("companyName")) {
            company.setCompanyName((String) data.get("companyName"));
        }

        if (data.containsKey("registrationNumber")) {
            company.setRegistrationNumber((String) data.get("registrationNumber"));
        }

        if (data.containsKey("industryId")) {
            try {
                company.setIndustryId(((Number) data.get("industryId")).intValue());
            } catch (ClassCastException e) {
                errors.put("industryId", "Industry ID must be a number");
            }
        }

        if (data.containsKey("companySize")) {
            company.setCompanySize((String) data.get("companySize"));
        }

        if (data.containsKey("businessAddress")) {
            company.setBusinessAddress((String) data.get("businessAddress"));
        }

        if (data.containsKey("phoneNumber")) {
            company.setPhoneNumber((String) data.get("phoneNumber"));
        }

        if (data.containsKey("companyEmail")) {
            company.setCompanyEmail((String) data.get("companyEmail"));
        }

        if (data.containsKey("website")) {
            company.setWebsite((String) data.get("website"));
        }

        if (data.containsKey("timeZone")) {
            company.setTimeZone((String) data.get("timeZone"));
        }

        // Handle logo upload (Base64 encoded)
        if (data.containsKey("logo") && data.get("logo") != null) {
            String logoData = (String) data.get("logo");

            // Validate logo size
            if (logoData.length() > MAX_LOGO_SIZE * 1.33) { // Base64 is ~33% larger than binary
                errors.put("logo", "Logo file size exceeds the maximum allowed (2MB)");
            } else {
                // Store the Base64 data directly
                company.setLogo(logoData);
            }
        }

        // Validate company data
        validateCompany(company, errors);

        // Check for duplicate email
        try {
            if (company.getCompanyEmail() != null && !company.getCompanyEmail().isEmpty()) {
                var conditions = new LinkedList<Condition>();
                conditions.add(new Condition.Equals("companyEmail", company.getCompanyEmail()));

                Collection<Company> existingCompanies = storage.getObjects(Company.class, new Request(
                        new Columns.All(), Condition.merge(conditions)));

                if (!existingCompanies.isEmpty()) {
                    errors.put("companyEmail", "This email address is already in use");
                }
            }
        } catch (StorageException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Database error"))
                    .build();
        }

        // Return validation errors if any
        if (!errors.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("errors", errors))
                    .build();
        }

        // Save the company
        try {
            company.setId(storage.addObject(company, new Request(new Columns.Exclude("id"))));
            return Response.status(Response.Status.CREATED).entity(company).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to create company"))
                    .build();
        }
    }

    @Override
    public Response add(Company entity) throws Exception {
        // This method is kept for backward compatibility
        User user = permissionsService.getUser(getUserId());

        // Only SUPER_USER can create companies
        if (user.getRole() != UserRole.SUPER_USER) {
            throw new SecurityException("Administrator access required");
        }

        return super.add(entity);
    }

    @PATCH
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateCompany(@PathParam("id") long id, Map<String, Object> data) throws Exception {
        User user = permissionsService.getUser(getUserId());

        // Check permissions
        if (user.getRole() != UserRole.SUPER_USER
                && (user.getRole() != UserRole.ADMIN || user.getCompanyId() != id)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Company access denied"))
                    .build();
        }

        // Get existing company
        Company company;
        try {
            company = storage.getObject(Company.class, new Request(
                    new Columns.All(), new Condition.Equals("id", id)));

            if (company == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Company not found"))
                        .build();
            }
        } catch (StorageException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Database error"))
                    .build();
        }

        Map<String, String> errors = new HashMap<>();

        // Update company properties from the request data
        if (data.containsKey("companyName")) {
            company.setCompanyName((String) data.get("companyName"));
        }

        if (data.containsKey("registrationNumber")) {
            company.setRegistrationNumber((String) data.get("registrationNumber"));
        }

        if (data.containsKey("industryId")) {
            try {
                company.setIndustryId(((Number) data.get("industryId")).intValue());
            } catch (ClassCastException e) {
                errors.put("industryId", "Industry ID must be a number");
            }
        }

        if (data.containsKey("companySize")) {
            company.setCompanySize((String) data.get("companySize"));
        }

        if (data.containsKey("businessAddress")) {
            company.setBusinessAddress((String) data.get("businessAddress"));
        }

        if (data.containsKey("phoneNumber")) {
            company.setPhoneNumber((String) data.get("phoneNumber"));
        }

        if (data.containsKey("companyEmail")) {
            company.setCompanyEmail((String) data.get("companyEmail"));
        }

        if (data.containsKey("website")) {
            company.setWebsite((String) data.get("website"));
        }

        if (data.containsKey("timeZone")) {
            company.setTimeZone((String) data.get("timeZone"));
        }

        // Handle logo upload (Base64 encoded)
        if (data.containsKey("logo") && data.get("logo") != null) {
            String logoData = (String) data.get("logo");

            // Validate logo size
            if (logoData.length() > MAX_LOGO_SIZE * 1.33) { // Base64 is ~33% larger than binary
                errors.put("logo", "Logo file size exceeds the maximum allowed (2MB)");
            } else {
                // Store the Base64 data directly
                company.setLogo(logoData);
            }
        }

        // Validate company data
        validateCompany(company, errors);

        // Check for duplicate email
        try {
            if (company.getCompanyEmail() != null && !company.getCompanyEmail().isEmpty()) {
                var conditions = new LinkedList<Condition>();
                conditions.add(new Condition.Equals("companyEmail", company.getCompanyEmail()));
                conditions.add(new Condition.Compare("id", "!=", id));

                Collection<Company> existingCompanies = storage.getObjects(Company.class, new Request(
                        new Columns.All(), Condition.merge(conditions)));

                if (!existingCompanies.isEmpty()) {
                    errors.put("companyEmail", "This email address is already in use");
                }
            }
        } catch (StorageException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Database error"))
                    .build();
        }

        // Return validation errors if any
        if (!errors.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("errors", errors))
                    .build();
        }

        // Update the company
        try {
            storage.updateObject(company, new Request(new Columns.All()));
            return Response.ok(company).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to update company"))
                    .build();
        }
    }

    @Override
    public Response update(Company entity) throws Exception {
        // This method is kept for backward compatibility
        User user = permissionsService.getUser(getUserId());

        // SUPER_USER can update any company
        if (user.getRole() == UserRole.SUPER_USER) {
            return super.update(entity);
        }

        // ADMIN can only update their own company
        if (user.getRole() == UserRole.ADMIN && user.getCompanyId() == entity.getId()) {
            return super.update(entity);
        }

        throw new SecurityException("Company access denied");
    }

    @Override
    public Response remove(long id) throws Exception {
        User user = permissionsService.getUser(getUserId());

        // Only SUPER_USER can delete companies
        if (user.getRole() != UserRole.SUPER_USER) {
            throw new SecurityException("Administrator access required");
        }

        return super.remove(id);
    }
}

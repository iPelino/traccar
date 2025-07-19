/*
 * Copyright 2017 - 2024 Anton Tananaev (anton@traccar.org)
 * Copyright 2017 Andrey Kunitsyn (andrey@traccar.org)
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

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.traccar.api.ExtendedObjectResource;
import org.traccar.model.Driver;
import org.traccar.model.DriverIncome;
import org.traccar.model.User;
import org.traccar.model.UserRole;
import org.traccar.reports.DriverIncomeReport;
import org.traccar.reports.model.DriverIncomeReportItem;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import jakarta.inject.Inject;
import java.util.Date;

import java.util.Collection;
import java.util.LinkedList;

@Path("drivers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DriverResource extends ExtendedObjectResource<Driver> {

    @Inject
    private DriverIncomeReport driverIncomeReport;

    public DriverResource() {
        super(Driver.class, "name");
    }

    @GET
    @Override
    public Collection<Driver> get(
            @QueryParam("all") boolean all, @QueryParam("userId") long userId,
            @QueryParam("groupId") long groupId, @QueryParam("deviceId") long deviceId) throws StorageException {

        User user = permissionsService.getUser(getUserId());

        // If user is not a SUPER_USER, filter drivers based on company
        if (user.getRole() != UserRole.SUPER_USER) {
            var conditions = new LinkedList<Condition>();

            // Admin, Company User, and Finance User can only see drivers in their company
            if (user.getCompanyId() > 0) {
                conditions.add(new Condition.Equals("companyId", user.getCompanyId()));
            } else {
                // User without company can't see any drivers
                return new LinkedList<>();
            }

            // Apply standard permission filters
            if (all) {
                if (permissionsService.notAdmin(getUserId())) {
                    conditions.add(new Condition.Permission(User.class, getUserId(), baseClass));
                }
            } else {
                if (userId == 0) {
                    userId = getUserId();
                } else {
                    permissionsService.checkUser(getUserId(), userId);
                }
                conditions.add(new Condition.Permission(User.class, userId, baseClass));
            }

            return storage.getObjects(baseClass, new Request(
                    new Columns.All(), Condition.merge(conditions)));
        }

        // For SUPER_USER, use the default behavior
        return super.get(all, userId, groupId, deviceId);
    }

    @Override
    public Response add(Driver entity) throws Exception {
        User user = permissionsService.getUser(getUserId());

        // Set company ID based on the user's company
        if (user.getRole() != UserRole.SUPER_USER) {
            entity.setCompanyId(user.getCompanyId());
        }

        return super.add(entity);
    }

    @Override
    public Response update(Driver entity) throws Exception {
        User user = permissionsService.getUser(getUserId());

        // SUPER_USER can update any driver
        if (user.getRole() == UserRole.SUPER_USER) {
            return super.update(entity);
        }

        // Get the existing driver to check company ID
        Driver driver = storage.getObject(Driver.class, new Request(
                new Columns.All(), new Condition.Equals("id", entity.getId())));

        // Check if driver exists and belongs to the user's company
        if (driver != null && driver.getCompanyId() == user.getCompanyId()) {
            // Ensure company ID is not changed
            entity.setCompanyId(user.getCompanyId());
            return super.update(entity);
        }

        throw new SecurityException("Driver access denied");
    }

    @Override
    public Response remove(long id) throws Exception {
        User user = permissionsService.getUser(getUserId());

        // SUPER_USER can delete any driver
        if (user.getRole() == UserRole.SUPER_USER) {
            return super.remove(id);
        }

        // Get the driver to check company ID
        Driver driver = storage.getObject(Driver.class, new Request(
                new Columns.All(), new Condition.Equals("id", id)));

        // Check if driver exists and belongs to the user's company
        if (driver != null && driver.getCompanyId() == user.getCompanyId()) {
            return super.remove(id);
        }

        throw new SecurityException("Driver access denied");
    }

    @Path("{id}/income")
    @POST
    public Response addIncome(
            @PathParam("id") long driverId,
            DriverIncome entity) throws StorageException {

        permissionsService.checkPermission(Driver.class, getUserId(), driverId);

        User user = permissionsService.getUser(getUserId());
        Driver driver = storage.getObject(Driver.class, new Request(
                new Columns.All(), new Condition.Equals("id", driverId)));

        if (driver == null) {
            throw new StorageException("Driver not found");
        }

        // Set driver ID and company ID
        entity.setDriverId(driverId);

        // Set company ID based on the driver's company
        if (driver.getCompanyId() > 0) {
            entity.setCompanyId(driver.getCompanyId());
        } else if (user.getRole() != UserRole.SUPER_USER && user.getCompanyId() > 0) {
            entity.setCompanyId(user.getCompanyId());
        }

        // Ensure date is set
        if (entity.getDate() == null) {
            entity.setDate(new Date());
        }

        entity.setId(storage.addObject(entity, new Request(new Columns.Exclude("id"))));

        return Response.ok(entity).build();
    }

    @Path("{id}/income")
    @GET
    public Collection<DriverIncomeReportItem> getIncome(
            @PathParam("id") long driverId,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to,
            @QueryParam("period") String period) throws StorageException {

        permissionsService.checkPermission(Driver.class, getUserId(), driverId);

        return driverIncomeReport.getObjects(getUserId(), driverId, from, to, period);
    }

    @Path("{id}/income/summary")
    @GET
    public Collection<DriverIncomeReportItem> getIncomeSummary(
            @PathParam("id") long driverId,
            @QueryParam("period") String period) throws StorageException {

        permissionsService.checkPermission(Driver.class, getUserId(), driverId);

        return driverIncomeReport.getSummary(getUserId(), driverId, period);
    }
}

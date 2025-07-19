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

import org.traccar.api.BaseResource;
import org.traccar.model.Device;
import org.traccar.model.Driver;
import org.traccar.model.DriverPayment;
import org.traccar.model.User;
import org.traccar.model.UserRole;
import org.traccar.model.VehicleExpense;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

@Path("finance")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FinanceResource extends BaseResource {

    @Path("payments")
    @POST
    public Response addPayment(DriverPayment entity) throws StorageException {
        User user = permissionsService.getUser(getUserId());

        // Check if driver exists
        Driver driver = storage.getObject(Driver.class, new Request(
                new Columns.All(), new Condition.Equals("id", entity.getDriverId())));

        if (driver == null) {
            throw new StorageException("Driver not found");
        }

        // Check permissions
        permissionsService.checkPermission(Driver.class, getUserId(), entity.getDriverId());

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

    @Path("payments")
    @GET
    public Collection<DriverPayment> getPayments(
            @QueryParam("driverId") long driverId,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to) throws StorageException {

        User user = permissionsService.getUser(getUserId());

        // Build query conditions
        var conditions = new ArrayList<Condition>();

        // Filter by driver if specified
        if (driverId > 0) {
            permissionsService.checkPermission(Driver.class, getUserId(), driverId);
            conditions.add(new Condition.Equals("driverId", driverId));
        }

        // Filter by date range
        if (from != null && to != null) {
            conditions.add(new Condition.Between("date", from, to));
        }

        // Apply company-based filtering for non-super users
        if (user.getRole() != UserRole.SUPER_USER && user.getCompanyId() > 0) {
            conditions.add(new Condition.Equals("companyId", user.getCompanyId()));
        } else if (user.getRole() != UserRole.SUPER_USER) {
            // For non-super users without a company, only show payments for drivers they have permission to
            var permittedDrivers = new LinkedList<Long>();
            List<Driver> drivers = storage.getObjects(Driver.class, new Request(
                    new Columns.Include("id"),
                    new Condition.Permission(User.class, getUserId(), Driver.class)));
            for (Driver driver : drivers) {
                permittedDrivers.add(driver.getId());
            }
            if (permittedDrivers.isEmpty()) {
                return new ArrayList<>();
            }

            // Create OR conditions for each driver ID
            Condition driverCondition = null;
            for (Long id : permittedDrivers) {
                Condition equals = new Condition.Equals("driverId", id);
                if (driverCondition == null) {
                    driverCondition = equals;
                } else {
                    driverCondition = new Condition.Or(driverCondition, equals);
                }
            }
            conditions.add(driverCondition);
        }

        // Get payments
        return storage.getObjects(DriverPayment.class, new Request(
                new Columns.All(),
                Condition.merge(conditions),
                new Order("date")));
    }

    @Path("payments/{id}")
    @DELETE
    public Response removePayment(@PathParam("id") long id) throws StorageException {
        User user = permissionsService.getUser(getUserId());

        // Get the payment
        DriverPayment payment = storage.getObject(DriverPayment.class, new Request(
                new Columns.All(), new Condition.Equals("id", id)));

        if (payment == null) {
            throw new StorageException("Payment not found");
        }

        // Check permissions
        if (user.getRole() == UserRole.SUPER_USER) {
            // Super user can delete any payment
            storage.removeObject(DriverPayment.class, new Request(
                    new Condition.Equals("id", id)));
            return Response.noContent().build();
        }

        // For non-super users, check if they have permission to the driver and company
        permissionsService.checkPermission(Driver.class, getUserId(), payment.getDriverId());

        if (user.getCompanyId() > 0 && payment.getCompanyId() == user.getCompanyId()) {
            storage.removeObject(DriverPayment.class, new Request(
                    new Condition.Equals("id", id)));
            return Response.noContent().build();
        }

        throw new SecurityException("Payment access denied");
    }

    @Path("expenses")
    @POST
    public Response addExpense(VehicleExpense entity) throws StorageException {
        User user = permissionsService.getUser(getUserId());

        // Check if vehicle exists
        Device vehicle = storage.getObject(Device.class, new Request(
                new Columns.All(), new Condition.Equals("id", entity.getVehicleId())));

        if (vehicle == null) {
            throw new StorageException("Vehicle not found");
        }

        // Check permissions
        permissionsService.checkPermission(Device.class, getUserId(), entity.getVehicleId());

        // Set company ID based on the vehicle's company
        if (vehicle.getGroupId() > 0) {
            // Try to get company ID from the vehicle's group
            // For simplicity, we'll use the user's company ID if available
            if (user.getRole() != UserRole.SUPER_USER && user.getCompanyId() > 0) {
                entity.setCompanyId(user.getCompanyId());
            }
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

    @Path("expenses")
    @GET
    public Collection<VehicleExpense> getExpenses(
            @QueryParam("vehicleId") long vehicleId,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to) throws StorageException {

        User user = permissionsService.getUser(getUserId());

        // Build query conditions
        var conditions = new ArrayList<Condition>();

        // Filter by vehicle if specified
        if (vehicleId > 0) {
            permissionsService.checkPermission(Device.class, getUserId(), vehicleId);
            conditions.add(new Condition.Equals("vehicleId", vehicleId));
        }

        // Filter by date range
        if (from != null && to != null) {
            conditions.add(new Condition.Between("date", from, to));
        }

        // Apply company-based filtering for non-super users
        if (user.getRole() != UserRole.SUPER_USER && user.getCompanyId() > 0) {
            conditions.add(new Condition.Equals("companyId", user.getCompanyId()));
        } else if (user.getRole() != UserRole.SUPER_USER) {
            // For non-super users without a company, only show expenses for vehicles they have permission to
            var permittedVehicles = new LinkedList<Long>();
            List<Device> vehicles = storage.getObjects(Device.class, new Request(
                    new Columns.Include("id"),
                    new Condition.Permission(User.class, getUserId(), Device.class)));
            for (Device vehicle : vehicles) {
                permittedVehicles.add(vehicle.getId());
            }
            if (permittedVehicles.isEmpty()) {
                return new ArrayList<>();
            }

            // Create OR conditions for each vehicle ID
            Condition vehicleCondition = null;
            for (Long id : permittedVehicles) {
                Condition equals = new Condition.Equals("vehicleId", id);
                if (vehicleCondition == null) {
                    vehicleCondition = equals;
                } else {
                    vehicleCondition = new Condition.Or(vehicleCondition, equals);
                }
            }
            conditions.add(vehicleCondition);
        }

        // Get expenses
        return storage.getObjects(VehicleExpense.class, new Request(
                new Columns.All(),
                Condition.merge(conditions),
                new Order("date")));
    }

    @Path("expenses/{id}")
    @DELETE
    public Response removeExpense(@PathParam("id") long id) throws StorageException {
        User user = permissionsService.getUser(getUserId());

        // Get the expense
        VehicleExpense expense = storage.getObject(VehicleExpense.class, new Request(
                new Columns.All(), new Condition.Equals("id", id)));

        if (expense == null) {
            throw new StorageException("Expense not found");
        }

        // Check permissions
        if (user.getRole() == UserRole.SUPER_USER) {
            // Super user can delete any expense
            storage.removeObject(VehicleExpense.class, new Request(
                    new Condition.Equals("id", id)));
            return Response.noContent().build();
        }

        // For non-super users, check if they have permission to the vehicle and company
        permissionsService.checkPermission(Device.class, getUserId(), expense.getVehicleId());

        if (user.getCompanyId() > 0 && expense.getCompanyId() == user.getCompanyId()) {
            storage.removeObject(VehicleExpense.class, new Request(
                    new Condition.Equals("id", id)));
            return Response.noContent().build();
        }

        throw new SecurityException("Expense access denied");
    }
}

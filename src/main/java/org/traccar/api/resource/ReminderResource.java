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
import org.traccar.model.Device;
import org.traccar.model.Reminder;
import org.traccar.model.User;
import org.traccar.model.UserRole;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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

@Path("reminders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReminderResource extends ExtendedObjectResource<Reminder> {

    public ReminderResource() {
        super(Reminder.class, "type");
    }

    @GET
    @Override
    public Collection<Reminder> get(
            @QueryParam("all") boolean all, @QueryParam("userId") long userId,
            @QueryParam("groupId") long groupId, @QueryParam("deviceId") long deviceId) throws StorageException {

        User user = permissionsService.getUser(getUserId());

        // Build query conditions
        var conditions = new ArrayList<Condition>();

        // Filter by device if specified
        if (deviceId > 0) {
            permissionsService.checkPermission(Device.class, getUserId(), deviceId);
            conditions.add(new Condition.Equals("vehicleId", deviceId));
        }

        // Apply company-based filtering for non-super users
        if (user.getRole() != UserRole.SUPER_USER && user.getCompanyId() > 0) {
            conditions.add(new Condition.Equals("companyId", user.getCompanyId()));
        } else if (user.getRole() != UserRole.SUPER_USER) {
            // For non-super users without a company, only show reminders for vehicles they have permission to
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

        // Get reminders
        return storage.getObjects(baseClass, new Request(
                new Columns.All(),
                Condition.merge(conditions),
                new Order("dueDate")));
    }

    @Path("upcoming")
    @GET
    public Collection<Reminder> getUpcoming(
            @QueryParam("days") int days,
            @QueryParam("deviceId") long deviceId) throws StorageException {

        User user = permissionsService.getUser(getUserId());

        // Build query conditions
        var conditions = new ArrayList<Condition>();

        // Filter by device if specified
        if (deviceId > 0) {
            permissionsService.checkPermission(Device.class, getUserId(), deviceId);
            conditions.add(new Condition.Equals("vehicleId", deviceId));
        }

        // Filter for incomplete reminders
        conditions.add(new Condition.Equals("completed", false));

        // Filter for reminders due in the next X days
        if (days > 0) {
            Date now = new Date();
            Date future = new Date(now.getTime() + days * 24 * 60 * 60 * 1000L);
            conditions.add(new Condition.Between("dueDate", now, future));
        }

        // Apply company-based filtering for non-super users
        if (user.getRole() != UserRole.SUPER_USER && user.getCompanyId() > 0) {
            conditions.add(new Condition.Equals("companyId", user.getCompanyId()));
        } else if (user.getRole() != UserRole.SUPER_USER) {
            // For non-super users without a company, only show reminders for vehicles they have permission to
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

        // Get reminders
        return storage.getObjects(baseClass, new Request(
                new Columns.All(),
                Condition.merge(conditions),
                new Order("dueDate")));
    }

    @Path("{id}/complete")
    @GET
    public Response completeReminder(@PathParam("id") long id) throws StorageException {
        User user = permissionsService.getUser(getUserId());

        // Get the reminder
        Reminder reminder = storage.getObject(Reminder.class, new Request(
                new Columns.All(), new Condition.Equals("id", id)));

        if (reminder == null) {
            throw new StorageException("Reminder not found");
        }

        // Check permissions
        permissionsService.checkPermission(Device.class, getUserId(), reminder.getVehicleId());

        // Check company permissions for non-super users
        if (user.getRole() != UserRole.SUPER_USER && user.getCompanyId() > 0 
                && reminder.getCompanyId() != user.getCompanyId()) {
            throw new SecurityException("Reminder access denied");
        }

        // Mark as completed
        reminder.setCompleted(true);
        reminder.setCompletedDate(new Date());

        storage.updateObject(reminder, new Request(
                new Columns.Exclude("id"),
                new Condition.Equals("id", id)));

        return Response.ok(reminder).build();
    }

    @Override
    public Response add(Reminder entity) throws Exception {
        User user = permissionsService.getUser(getUserId());

        // Check if vehicle exists
        Device vehicle = storage.getObject(Device.class, new Request(
                new Columns.All(), new Condition.Equals("id", entity.getVehicleId())));

        if (vehicle == null) {
            throw new StorageException("Vehicle not found");
        }

        // Check permissions
        permissionsService.checkPermission(Device.class, getUserId(), entity.getVehicleId());

        // Set company ID based on the user's company
        if (user.getRole() != UserRole.SUPER_USER && user.getCompanyId() > 0) {
            entity.setCompanyId(user.getCompanyId());
        }

        return super.add(entity);
    }

    @Override
    public Response update(Reminder entity) throws Exception {
        User user = permissionsService.getUser(getUserId());

        // Get the existing reminder
        Reminder reminder = storage.getObject(Reminder.class, new Request(
                new Columns.All(), new Condition.Equals("id", entity.getId())));

        if (reminder == null) {
            throw new StorageException("Reminder not found");
        }

        // Check permissions
        permissionsService.checkPermission(Device.class, getUserId(), reminder.getVehicleId());

        // Check company permissions for non-super users
        if (user.getRole() != UserRole.SUPER_USER && user.getCompanyId() > 0 
                && reminder.getCompanyId() != user.getCompanyId()) {
            throw new SecurityException("Reminder access denied");
        }

        // Ensure company ID is not changed for non-super users
        if (user.getRole() != UserRole.SUPER_USER) {
            entity.setCompanyId(reminder.getCompanyId());
        }

        return super.update(entity);
    }

    @Override
    public Response remove(long id) throws Exception {
        User user = permissionsService.getUser(getUserId());

        // Get the reminder
        Reminder reminder = storage.getObject(Reminder.class, new Request(
                new Columns.All(), new Condition.Equals("id", id)));

        if (reminder == null) {
            throw new StorageException("Reminder not found");
        }

        // Check permissions
        permissionsService.checkPermission(Device.class, getUserId(), reminder.getVehicleId());

        // Check company permissions for non-super users
        if (user.getRole() != UserRole.SUPER_USER && user.getCompanyId() > 0 
                && reminder.getCompanyId() != user.getCompanyId()) {
            throw new SecurityException("Reminder access denied");
        }

        return super.remove(id);
    }
}
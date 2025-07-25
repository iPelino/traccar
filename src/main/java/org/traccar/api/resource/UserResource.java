/*
 * Copyright 2015 - 2024 Anton Tananaev (anton@traccar.org)
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

import com.warrenstrange.googleauth.GoogleAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import org.traccar.api.BaseObjectResource;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.helper.LogAction;
import org.traccar.helper.SessionHelper;
import org.traccar.helper.model.UserUtil;
import org.traccar.model.Device;
import org.traccar.model.ManagedUser;
import org.traccar.model.Permission;
import org.traccar.model.User;
import org.traccar.model.UserRole;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Collection;
import java.util.LinkedList;

@Path("users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource extends BaseObjectResource<User> {

    @Inject
    private Config config;

    @Inject
    private LogAction actionLogger;

    @Context
    private HttpServletRequest request;

    public UserResource() {
        super(User.class);
    }

    @GET
    public Collection<User> get(
            @QueryParam("userId") long userId, @QueryParam("deviceId") long deviceId,
            @QueryParam("companyId") long companyId) throws StorageException {
        var conditions = new LinkedList<Condition>();
        User user = permissionsService.getUser(getUserId());

        // Handle company-based filtering
        if (user.getRole() != null) {
            switch (user.getRole()) {
                case SUPER_USER:
                    // Super user can see all users or filter by company
                    if (companyId > 0) {
                        conditions.add(new Condition.Equals("companyId", companyId));
                    }
                    break;
                case ADMIN:
                    // Admin can only see users in their company
                    if (companyId > 0 && companyId != user.getCompanyId()) {
                        throw new SecurityException("Company access denied");
                    }
                    conditions.add(new Condition.Equals("companyId", user.getCompanyId()));
                    break;
                case COMPANY_USER:
                case FINANCE_USER:
                    // Regular users can only see themselves and users they manage
                    if (userId > 0) {
                        permissionsService.checkUser(getUserId(), userId);
                        conditions.add(new Condition.Permission(User.class, userId, ManagedUser.class).excludeGroups());
                    } else {
                        conditions.add(new Condition.Permission(User.class, getUserId(), ManagedUser.class)
                                .excludeGroups());
                    }
                    break;
                default:
                    // Legacy behavior for users without roles
                    if (userId > 0) {
                        permissionsService.checkUser(getUserId(), userId);
                        conditions.add(new Condition.Permission(User.class, userId, ManagedUser.class).excludeGroups());
                    } else if (permissionsService.notAdmin(getUserId())) {
                        conditions.add(new Condition.Permission(User.class, getUserId(), ManagedUser.class)
                                .excludeGroups());
                    }
            }
        } else {
            // Legacy behavior for users without roles
            if (userId > 0) {
                permissionsService.checkUser(getUserId(), userId);
                conditions.add(new Condition.Permission(User.class, userId, ManagedUser.class).excludeGroups());
            } else if (permissionsService.notAdmin(getUserId())) {
                conditions.add(new Condition.Permission(User.class, getUserId(), ManagedUser.class).excludeGroups());
            }
        }

        if (deviceId > 0) {
            permissionsService.checkManager(getUserId());
            conditions.add(new Condition.Permission(User.class, Device.class, deviceId).excludeGroups());
        }

        return storage.getObjects(baseClass, new Request(
                new Columns.All(), Condition.merge(conditions), new Order("name")));
    }

    @Override
    @PermitAll
    @POST
    public Response add(User entity) throws StorageException {
        User currentUser = getUserId() > 0 ? permissionsService.getUser(getUserId()) : null;

        // Handle role-based user creation
        if (currentUser != null && currentUser.getRole() != null) {
            switch (currentUser.getRole()) {
                case SUPER_USER:
                    // Super user can create any user with any role
                    // If no role is specified, default to COMPANY_USER
                    if (entity.getRole() == null) {
                        entity.setRole(UserRole.COMPANY_USER);
                    }
                    break;
                case ADMIN:
                    // Admin can only create users for their own company with roles COMPANY_USER or FINANCE_USER
                    if (entity.getRole() == UserRole.SUPER_USER || entity.getRole() == UserRole.ADMIN) {
                        throw new SecurityException("Cannot create user with this role");
                    }

                    // Set company ID to admin's company
                    entity.setCompanyId(currentUser.getCompanyId());

                    // If no role is specified, default to COMPANY_USER
                    if (entity.getRole() == null) {
                        entity.setRole(UserRole.COMPANY_USER);
                    }

                    // Check user limit
                    if (currentUser.getUserLimit() > 0) {
                        int userCount = storage.getObjects(baseClass, new Request(
                                new Columns.All(),
                                new Condition.And(
                                    new Condition.Permission(User.class, getUserId(), ManagedUser.class)
                                            .excludeGroups(),
                                    new Condition.Equals("companyId", currentUser.getCompanyId())
                                ))).size();
                        if (userCount >= currentUser.getUserLimit()) {
                            throw new SecurityException("Manager user limit reached");
                        }
                    }
                    break;
                case COMPANY_USER:
                case FINANCE_USER:
                    // Regular users cannot create new users
                    throw new SecurityException("User creation not allowed");
                default:
                    // Legacy behavior for users without roles
                    handleLegacyUserCreation(currentUser, entity);
            }
        } else if (currentUser == null || !currentUser.getAdministrator()) {
            // Legacy behavior for users without roles or non-admin users
            handleLegacyUserCreation(currentUser, entity);
        }

        entity.setId(storage.addObject(entity, new Request(new Columns.Exclude("id"))));
        storage.updateObject(entity, new Request(
                new Columns.Include("hashedPassword", "salt"),
                new Condition.Equals("id", entity.getId())));

        actionLogger.create(request, getUserId(), entity);

        if (currentUser != null
                && (currentUser.getUserLimit() != 0
                || (currentUser.getRole() == UserRole.ADMIN || currentUser.getRole() == UserRole.SUPER_USER))) {
            storage.addPermission(new Permission(User.class, getUserId(), ManagedUser.class, entity.getId()));
            actionLogger.link(request, getUserId(), User.class, getUserId(), ManagedUser.class, entity.getId());
        }
        return Response.ok(entity).build();
    }

    private void handleLegacyUserCreation(User currentUser, User entity) throws StorageException {
        permissionsService.checkUserUpdate(getUserId(), new User(), entity);
        if (currentUser != null && currentUser.getUserLimit() != 0) {
            int userLimit = currentUser.getUserLimit();
            if (userLimit > 0) {
                int userCount = storage.getObjects(baseClass, new Request(
                        new Columns.All(),
                        new Condition.Permission(User.class, getUserId(), ManagedUser.class).excludeGroups()))
                        .size();
                if (userCount >= userLimit) {
                    throw new SecurityException("Manager user limit reached");
                }
            }
        } else {
            if (UserUtil.isEmpty(storage)) {
                entity.setAdministrator(true);
                entity.setRole(UserRole.SUPER_USER);
            } else if (!permissionsService.getServer().getRegistration()) {
                throw new SecurityException("Registration disabled");
            }
            if (permissionsService.getServer().getBoolean(Keys.WEB_TOTP_FORCE.getKey())
                    && entity.getTotpKey() == null) {
                throw new SecurityException("One-time password key is required");
            }
            UserUtil.setUserDefaults(entity, config);
        }
    }

    @Path("{id}")
    @DELETE
    public Response remove(@PathParam("id") long id) throws Exception {
        User currentUser = permissionsService.getUser(getUserId());
        User targetUser = storage.getObject(User.class, new Request(
                new Columns.All(), new Condition.Equals("id", id)));

        if (targetUser == null) {
            throw new StorageException(new IllegalArgumentException("User not found"));
        }

        // Role-based permission checks
        if (currentUser.getRole() != null) {
            switch (currentUser.getRole()) {
                case SUPER_USER:
                    // Super user can delete any user except themselves
                    break;
                case ADMIN:
                    // Admin can only delete users in their company with lower roles
                    if (targetUser.getCompanyId() != currentUser.getCompanyId()) {
                        throw new SecurityException("Cannot delete user from another company");
                    }
                    if (targetUser.getRole() == UserRole.SUPER_USER || targetUser.getRole() == UserRole.ADMIN) {
                        throw new SecurityException("Cannot delete user with this role");
                    }
                    break;
                case COMPANY_USER:
                case FINANCE_USER:
                    // Regular users cannot delete other users
                    throw new SecurityException("User deletion not allowed");
                default:
                    // Legacy behavior
                    break;
            }
        }

        Response response = super.remove(id);
        if (getUserId() == id) {
            request.getSession().removeAttribute(SessionHelper.USER_ID_KEY);
        }
        return response;
    }

    @Path("totp")
    @PermitAll
    @POST
    public String generateTotpKey() throws StorageException {
        if (!permissionsService.getServer().getBoolean(Keys.WEB_TOTP_ENABLE.getKey())) {
            throw new SecurityException("One-time password is disabled");
        }
        return new GoogleAuthenticator().createCredentials().getKey();
    }

}

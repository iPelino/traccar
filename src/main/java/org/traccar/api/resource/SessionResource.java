/*
 * Copyright 2015 - 2025 Anton Tananaev (anton@traccar.org)
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
import org.traccar.api.security.CodeRequiredException;
import org.traccar.api.security.LoginResult;
import org.traccar.api.security.LoginService;
import org.traccar.api.signature.TokenManager;
import org.traccar.database.OpenIdProvider;
import org.traccar.helper.LogAction;
import org.traccar.helper.SessionHelper;
import org.traccar.model.User;
import org.traccar.model.UserRole;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.nimbusds.oauth2.sdk.ParseException;
import jakarta.annotation.Nullable;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;

@Path("session")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
public class SessionResource extends BaseResource {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserSession {
        private User user;
        private UserRole role;
        private Long companyId;

        public UserSession(User user) {
            this.user = user;
            this.role = user.getRole();
            this.companyId = user.getCompanyId() > 0 ? user.getCompanyId() : null;
        }

        public User getUser() {
            return user;
        }

        public void setUser(User user) {
            this.user = user;
        }

        public UserRole getRole() {
            return role;
        }

        public void setRole(UserRole role) {
            this.role = role;
        }

        public Long getCompanyId() {
            return companyId;
        }

        public void setCompanyId(Long companyId) {
            this.companyId = companyId;
        }
    }

    @Inject
    private LoginService loginService;

    @Inject
    @Nullable
    private OpenIdProvider openIdProvider;

    @Inject
    private TokenManager tokenManager;

    @Inject
    private LogAction actionLogger;

    @Context
    private HttpServletRequest request;

    @PermitAll
    @GET
    public UserSession get(@QueryParam("token") String token)
            throws StorageException, IOException, GeneralSecurityException {

        if (token != null) {
            LoginResult loginResult = loginService.login(token);
            if (loginResult != null) {
                User user = loginResult.getUser();
                SessionHelper.userLogin(actionLogger, request, user, loginResult.getExpiration());
                return new UserSession(user);
            }
        }

        Long userId = (Long) request.getSession().getAttribute(SessionHelper.USER_ID_KEY);
        if (userId != null) {
            User user = permissionsService.getUser(userId);
            if (user != null) {
                return new UserSession(user);
            }
        }

        throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Path("{id}")
    @GET
    public UserSession get(@PathParam("id") long userId) throws StorageException {
        permissionsService.checkUser(getUserId(), userId);
        User user = storage.getObject(User.class, new Request(
                new Columns.All(), new Condition.Equals("id", userId)));
        SessionHelper.userLogin(actionLogger, request, user, null);
        return new UserSession(user);
    }

    @PermitAll
    @POST
    public UserSession add(
            @FormParam("email") String email,
            @FormParam("password") String password,
            @FormParam("code") Integer code) throws StorageException {
        LoginResult loginResult;
        try {
            loginResult = loginService.login(email, password, code);
        } catch (CodeRequiredException e) {
            Response response = Response
                    .status(Response.Status.UNAUTHORIZED)
                    .header("WWW-Authenticate", "TOTP")
                    .build();
            throw new WebApplicationException(response);
        }
        if (loginResult != null) {
            User user = loginResult.getUser();
            SessionHelper.userLogin(actionLogger, request, user, null);
            return new UserSession(user);
        } else {
            actionLogger.failedLogin(request);
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
    }

    @DELETE
    public Response remove() {
        actionLogger.logout(request, getUserId());
        request.getSession().removeAttribute(SessionHelper.USER_ID_KEY);
        return Response.noContent().build();
    }

    @Path("token")
    @POST
    public String requestToken(
            @FormParam("expiration") Date expiration) throws StorageException, GeneralSecurityException, IOException {
        Date currentExpiration = (Date) request.getSession().getAttribute(SessionHelper.EXPIRATION_KEY);
        if (currentExpiration != null && currentExpiration.before(expiration)) {
            expiration = currentExpiration;
        }
        return tokenManager.generateToken(getUserId(), expiration);
    }

    @PermitAll
    @Path("openid/auth")
    @GET
    public Response openIdAuth() {
        if (openIdProvider == null) {
            throw new UnsupportedOperationException("OpenID not enabled");
        }
        return Response.seeOther(openIdProvider.createAuthUri()).build();
    }

    @PermitAll
    @Path("openid/callback")
    @GET
    public Response requestToken() throws IOException, StorageException, ParseException, GeneralSecurityException {
        if (openIdProvider == null) {
            throw new UnsupportedOperationException("OpenID not enabled");
        }
        return Response.seeOther(openIdProvider.handleCallback(request.getQueryString(), request)).build();
    }

}

/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.management.rest.resource;

import io.gravitee.common.http.MediaType;
import io.gravitee.management.idp.api.authentication.UserDetails;
import io.gravitee.management.model.UpdateUserEntity;
import io.gravitee.management.security.JWTCookieGenerator;
import io.gravitee.management.service.UserService;
import io.gravitee.management.service.exceptions.ForbiddenAccessException;
import io.gravitee.management.service.exceptions.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.OAuth2Authentication;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/**
 * @author Azize Elamrani (azize at gravitee.io)
 * @author GraviteeSource Team
 */
@Path("/user")
public class UserResource extends AbstractResource {

    private static Logger LOG = LoggerFactory.getLogger(UserResource.class);

    @Autowired
    private UserService userService;

    @Autowired
    private JWTCookieGenerator jwtCookieGenerator;

    @Context
    private HttpServletResponse response;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response user() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication.getClass().isAssignableFrom(OAuth2Authentication.class)) {
            return Response.ok(convert(((OAuth2Authentication) authentication).getUserAuthentication()), MediaType.APPLICATION_JSON).build();
        }

        final Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            final String username = ((UserDetails) principal).getUsername();
            try {
                userService.findByName(username);
            } catch (final UserNotFoundException unfe) {
                LOG.info("User '{}' no longer exists.", username, unfe);
                return logout();
            }
            return Response.ok(principal, MediaType.APPLICATION_JSON).build();
        }
        return Response.ok().build();
    }

    @PUT
    @Path("/{username}")
    public Response update(@PathParam("username") final String username, @Valid @NotNull final UpdateUserEntity user) {
        if (!username.equals(getAuthenticatedUsername())) {
            throw new ForbiddenAccessException();
        }

        return Response.ok(userService.update(user)).build();
    }

    @GET
    @Path("/{username}/picture")
    public Response getPicture(@PathParam("username") final String username) {
        return Response.ok(userService.findByName(username).getPicture()).build();
    }

    @POST
    @Path("/login")
    public Response login() {
        return Response.ok().build();
    }

    @POST
    @Path("/logout")
    public Response logout() {
        response.addCookie(jwtCookieGenerator.generate(null));
        return Response.ok().build();
    }

    private UserDetails convert(Authentication userAuthentication) {
        return new UserDetails(userAuthentication.getName(),
                                                    userAuthentication.getCredentials().toString(),
                                                    userAuthentication.getAuthorities());
    }
}

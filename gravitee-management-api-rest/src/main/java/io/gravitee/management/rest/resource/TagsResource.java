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
import io.gravitee.management.model.NewTagEntity;
import io.gravitee.management.model.TagEntity;
import io.gravitee.management.model.UpdateTagEntity;
import io.gravitee.management.model.permissions.RolePermission;
import io.gravitee.management.model.permissions.RolePermissionAction;
import io.gravitee.management.rest.security.Permission;
import io.gravitee.management.rest.security.Permissions;
import io.gravitee.management.service.TagService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"Sharding Tags"})
public class TagsResource extends AbstractResource  {

    @Autowired
    private TagService tagService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List sharding tags")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List of sharding tags", response = TagEntity.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public List<TagEntity> list()  {
        return tagService.findAll()
                .stream()
                .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                .collect(Collectors.toList());
    }

    @GET
    @Path("{tag}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a sharding tag",
            notes = "User must have the MANAGEMENT_TAG[READ] permission to use this service")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Tag", response = TagEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_TAG, acls = RolePermissionAction.READ)
    })
    public TagEntity getTag(@PathParam("tag") String tag) {
        return tagService.findById(tag);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a sharding tag",
            notes = "User must have the MANAGEMENT_TAG[CREATE] permission to use this service")
    @ApiResponses({
            @ApiResponse(code = 201, message = "A new sharding tag", response = TagEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_TAG, acls = RolePermissionAction.CREATE)
    })
    public TagEntity create(@Valid @NotNull final NewTagEntity tag) {
        return tagService.create(tag);
    }

    @PUT
    @Path("{tag}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update an existing sharding tag",
            notes = "User must have the MANAGEMENT_TAG[UPDATE] permission to use this service")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Sharding tag", response = TagEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_TAG, acls = RolePermissionAction.UPDATE)
    })
    public TagEntity update(@PathParam("tag") String tagId, @Valid @NotNull final UpdateTagEntity tag) {
        return tagService.update(tag);
    }

    @Path("{tag}")
    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Delete an existing sharding tag",
            notes = "User must have the MANAGEMENT_TAG[DELETE] permission to use this service")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Sharding tag successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_TAG, acls = RolePermissionAction.DELETE)
    })
    public void delete(@PathParam("tag") String tag) {
        tagService.delete(tag);
    }
}

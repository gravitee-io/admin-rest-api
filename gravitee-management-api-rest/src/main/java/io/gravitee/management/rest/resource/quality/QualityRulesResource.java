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
package io.gravitee.management.rest.resource.quality;

import io.gravitee.common.http.MediaType;
import io.gravitee.management.model.permissions.RolePermission;
import io.gravitee.management.model.permissions.RolePermissionAction;
import io.gravitee.management.model.quality.NewQualityRuleEntity;
import io.gravitee.management.model.quality.QualityRuleEntity;
import io.gravitee.management.rest.resource.AbstractResource;
import io.gravitee.management.rest.security.Permission;
import io.gravitee.management.rest.security.Permissions;
import io.gravitee.management.service.QualityRuleService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import java.util.List;

import static io.gravitee.common.http.MediaType.APPLICATION_JSON;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"Configuration"})
public class QualityRulesResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;
    @Autowired
    private QualityRuleService qualityRuleService;

    @GET
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "List quality rules")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List of quality rules", response = QualityRuleEntity.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public List<QualityRuleEntity> get() {
        return qualityRuleService.findAll();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a quality rule",
            notes = "User must have the MANAGEMENT_QUALITY_RULE[CREATE] permission to use this service")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Quality rule successfully created", response = QualityRuleEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_QUALITY_RULE, acls = RolePermissionAction.CREATE)
    })
    public QualityRuleEntity create(@Valid @NotNull final NewQualityRuleEntity newQualityRuleEntity) {
        return qualityRuleService.create(newQualityRuleEntity);
    }

    @Path("{id}")
    public QualityRuleResource getQualityRuleResource() {
        return resourceContext.getResource(QualityRuleResource.class);
    }
}

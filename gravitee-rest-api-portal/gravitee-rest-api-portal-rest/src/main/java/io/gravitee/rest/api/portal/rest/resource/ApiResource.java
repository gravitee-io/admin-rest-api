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
package io.gravitee.rest.api.portal.rest.resource;

import java.io.ByteArrayOutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import io.gravitee.common.http.MediaType;
import io.gravitee.rest.api.model.InlinePictureEntity;
import io.gravitee.rest.api.model.PlanEntity;
import io.gravitee.rest.api.model.PlanStatus;
import io.gravitee.rest.api.model.Visibility;
import io.gravitee.rest.api.model.api.ApiEntity;
import io.gravitee.rest.api.model.documentation.PageQuery;
import io.gravitee.rest.api.model.permissions.RolePermission;
import io.gravitee.rest.api.model.permissions.RolePermissionAction;
import io.gravitee.rest.api.portal.rest.mapper.ApiMapper;
import io.gravitee.rest.api.portal.rest.mapper.PageMapper;
import io.gravitee.rest.api.portal.rest.mapper.PlanMapper;
import io.gravitee.rest.api.portal.rest.model.Api;
import io.gravitee.rest.api.portal.rest.model.Page;
import io.gravitee.rest.api.portal.rest.model.Plan;
import io.gravitee.rest.api.service.GroupService;
import io.gravitee.rest.api.service.PageService;
import io.gravitee.rest.api.service.PlanService;
import io.gravitee.rest.api.service.exceptions.ForbiddenAccessException;

/**
 * @author Florent CHAMFROY (florent.chamfroy at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApiResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;
    @Context
    private UriInfo uriInfo;
    
    @Inject
    private ApiMapper apiMapper;
    @Inject
    private PageMapper pageMapper;
    @Inject
    private PlanMapper planMapper;
    
    @Inject
    private PageService pageService;
    @Inject
    private PlanService planService;
    @Inject
    private GroupService groupService;
    
    private static final String INCLUDE_PAGES = "pages";
    private static final String INCLUDE_PLANS = "plans";
    
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getApiByApiId(@PathParam("apiId") String apiId, @QueryParam("include") List<String> include) {
        
        ApiEntity apiEntity = apiService.findById(apiId);
        if (Visibility.PUBLIC.equals(apiEntity.getVisibility())
                || hasPermission(RolePermission.API_DEFINITION, apiId, RolePermissionAction.READ)) {

            Api api = apiMapper.convert(apiEntity);
            
            if(include.contains(INCLUDE_PAGES)) {
                List<Page> pages = pageService
                        .search(new PageQuery.Builder()
                                .api(apiId)
                                .build())
                        .stream()
                        .filter(page -> isDisplayable(apiEntity, page.isPublished(), page.getExcludedGroups()))
                        .map(pageMapper::convert)
                        .map(page-> page.content(null))
                        .collect(Collectors.toList());
                api.setPages(pages);
            }
            if(include.contains(INCLUDE_PLANS)) {
                String user = getAuthenticatedUserOrNull();
                List<Plan> plans = planService.findByApi(apiId).stream()
                        .filter(plan -> PlanStatus.PUBLISHED.equals(plan.getStatus()))
                        .filter(plan -> (isAuthenticated() || groupService.
                                isUserAuthorizedToAccessApiData(apiEntity, plan.getExcludedGroups(), getAuthenticatedUserOrNull())))
                        .sorted(Comparator.comparingInt(PlanEntity::getOrder))
                        .map(p->planMapper.convert(p, user))
                        .collect(Collectors.toList());
                api.setPlans(plans);
            }
        
            api.links(apiMapper.computeApiLinks(uriInfo.getAbsolutePath().toString()));
            
            return Response
                    .ok(api)
                    .build();
        }
        throw new ForbiddenAccessException();
    }

    private boolean isDisplayable(ApiEntity api, boolean isPagePublished, List<String> excludedGroups) {
        return (isAuthenticated())
                ||
                ( pageService.isDisplayable(api, isPagePublished, getAuthenticatedUserOrNull()) &&
                        groupService.isUserAuthorizedToAccessApiData(api, excludedGroups, getAuthenticatedUserOrNull()));

    }
    
    @GET
    @Path("picture")
    @Produces({MediaType.WILDCARD, MediaType.APPLICATION_JSON})
    public Response getPictureByApiId(@Context Request request, @PathParam("apiId") String apiId) {
        ApiEntity apiEntity = apiService.findById(apiId);
        if (Visibility.PUBLIC.equals(apiEntity.getVisibility())
                || hasPermission(RolePermission.API_DEFINITION, apiId, RolePermissionAction.READ)) {

            CacheControl cc = new CacheControl();
            cc.setNoTransform(true);
            cc.setMustRevalidate(false);
            cc.setNoCache(false);
            cc.setMaxAge(86400);

            InlinePictureEntity image = apiService.getPicture(apiId);

            EntityTag etag = new EntityTag(Integer.toString(new String(image.getContent()).hashCode()));
            Response.ResponseBuilder builder = request.evaluatePreconditions(etag);

            if (builder != null) {
                // Preconditions are not met, returning HTTP 304 'not-modified'
                return builder
                        .cacheControl(cc)
                        .build();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(image.getContent(), 0, image.getContent().length);

            return Response
                    .ok(baos)
                    .cacheControl(cc)
                    .tag(etag)
                    .type(image.getType())
                    .build();
        }
        throw new ForbiddenAccessException();
    }

    @Path("pages")
    public ApiPagesResource getApiPagesResource() {
        return resourceContext.getResource(ApiPagesResource.class);
    }

    @Path("plans")
    public ApiPlansResource getApiPlansResource() {
        return resourceContext.getResource(ApiPlansResource.class);
    }

    @Path("ratings")
    public ApiRatingsResource getRatingResource() {
        return resourceContext.getResource(ApiRatingsResource.class);
    }

}

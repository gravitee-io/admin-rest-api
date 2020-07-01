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
import io.gravitee.management.model.analytics.Analytics;
import io.gravitee.management.model.analytics.query.*;
import io.gravitee.management.model.api.ApiEntity;
import io.gravitee.management.model.application.ApplicationListItem;
import io.gravitee.management.rest.resource.param.Aggregation;
import io.gravitee.management.rest.resource.param.AnalyticsParam;
import io.gravitee.management.rest.resource.param.Range;
import io.gravitee.management.rest.security.Permission;
import io.gravitee.management.rest.security.Permissions;
import io.gravitee.management.service.AnalyticsService;
import io.gravitee.management.service.ApiService;
import io.gravitee.management.service.ApplicationService;
import io.gravitee.management.service.PermissionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import javax.inject.Inject;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.gravitee.management.model.permissions.RolePermission.*;
import static io.gravitee.management.model.permissions.RolePermissionAction.READ;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"Platform Analytics"})
public class PlatformAnalyticsResource extends AbstractResource  {

    @Inject
    private AnalyticsService analyticsService;

    @Inject
    ApiService apiService;

    @Inject
    PermissionService permissionService;

    @Inject
    ApplicationService applicationService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get platform analytics",
            notes = "User must have the MANAGEMENT_PLATFORM[READ] permission to use this service")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Platform analytics"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = MANAGEMENT_PLATFORM, acls = READ)
    })
    public Response platformAnalytics(@BeanParam AnalyticsParam analyticsParam) {

        analyticsParam.validate();

        Analytics analytics = null;

        // add filter by Apis or Applications
        String extraFilter = null;
        if (!isAdmin()) {
            String fieldName;
            List<String> ids;
            if ("application".equals(analyticsParam.getField())) {
                fieldName = "application";
                ids = applicationService.findByUser(getAuthenticatedUser())
                        .stream()
                        .filter(app -> permissionService.hasPermission(APPLICATION_ANALYTICS, app.getId(), READ))
                        .map(ApplicationListItem::getId)
                        .collect(Collectors.toList());
            } else {
                fieldName = "api";
                ids = apiService.findByUser(getAuthenticatedUser(), null)
                        .stream()
                        .filter(api -> permissionService.hasPermission(API_ANALYTICS, api.getId(), READ))
                        .map(ApiEntity::getId)
                        .collect(Collectors.toList());
            }

            if (ids.isEmpty()) {
                return Response.noContent().build();
            }
            extraFilter = getExtraFilter(fieldName, ids);
        }

        if (analyticsParam.getQuery() != null) {
            analyticsParam.setQuery(analyticsParam.getQuery().replaceAll("\\?", "1"));
        }

        switch (analyticsParam.getTypeParam().getValue()) {
            case DATE_HISTO:
                analytics = executeDateHisto(analyticsParam, extraFilter);
                break;
            case GROUP_BY:
                analytics = executeGroupBy(analyticsParam, extraFilter);
                break;
            case COUNT:
                analytics = executeCount(analyticsParam, extraFilter);
                break;
            case STATS:
                analytics = executeStats(analyticsParam, extraFilter);
                break;
        }

        return Response.ok(analytics).build();
    }

    private Analytics executeStats(AnalyticsParam analyticsParam, String extraFilter) {
        final StatsQuery query = new StatsQuery();
        query.setFrom(analyticsParam.getFrom());
        query.setTo(analyticsParam.getTo());
        query.setInterval(analyticsParam.getInterval());
        query.setQuery(analyticsParam.getQuery());
        query.setField(analyticsParam.getField());
        addExtraFilter(query, extraFilter);
        return analyticsService.execute(query);
    }

    private Analytics executeCount(AnalyticsParam analyticsParam, String extraFilter) {
        CountQuery query = new CountQuery();
        query.setFrom(analyticsParam.getFrom());
        query.setTo(analyticsParam.getTo());
        query.setInterval(analyticsParam.getInterval());
        query.setQuery(analyticsParam.getQuery());
        addExtraFilter(query, extraFilter);
        return analyticsService.execute(query);
    }

    private Analytics executeDateHisto(AnalyticsParam analyticsParam, String extraFilter) {
        DateHistogramQuery query = new DateHistogramQuery();
        query.setFrom(analyticsParam.getFrom());
        query.setTo(analyticsParam.getTo());
        query.setInterval(analyticsParam.getInterval());
        query.setQuery(analyticsParam.getQuery());
        List<Aggregation> aggregations = analyticsParam.getAggregations();
        if (aggregations != null) {
            List<io.gravitee.management.model.analytics.query.Aggregation> aggregationList = aggregations
                    .stream()
                    .map((Function<Aggregation, io.gravitee.management.model.analytics.query.Aggregation>) aggregation -> new io.gravitee.management.model.analytics.query.Aggregation() {
                        @Override
                        public AggregationType type() {
                            return AggregationType.valueOf(aggregation.getType().name().toUpperCase());
                        }

                        @Override
                        public String field() {
                            return aggregation.getField();
                        }
                    }).collect(Collectors.toList());

            query.setAggregations(aggregationList);
        }
        addExtraFilter(query, extraFilter);
        return analyticsService.execute(query);
    }

    private Analytics executeGroupBy(AnalyticsParam analyticsParam, String extraFilter) {
        GroupByQuery query = new GroupByQuery();
        query.setFrom(analyticsParam.getFrom());
        query.setTo(analyticsParam.getTo());
        query.setInterval(analyticsParam.getInterval());
        query.setQuery(analyticsParam.getQuery());
        query.setField(analyticsParam.getField());

        if (analyticsParam.getOrder() != null) {
            final GroupByQuery.Order order = new GroupByQuery.Order();
            order.setField(analyticsParam.getOrder().getField());
            order.setType(analyticsParam.getOrder().getType());
            order.setOrder(analyticsParam.getOrder().isOrder());
            query.setOrder(order);
        }

        List<Range> ranges = analyticsParam.getRanges();
        if (ranges != null) {
            Map<Double, Double> rangeMap = ranges.stream().collect(
                    Collectors.toMap(Range::getFrom, Range::getTo));

            query.setGroups(rangeMap);
        }
        addExtraFilter(query, extraFilter);
        return analyticsService.execute(query);
    }

    private void addExtraFilter(AbstractQuery query, String extraFilter) {
        if (query.getQuery() == null || query.getQuery().isEmpty()) {
            query.setQuery(extraFilter);
        } else if (extraFilter != null && ! extraFilter.isEmpty()) {
            query.setQuery(query.getQuery() + " AND " + extraFilter);
        } else {
            query.setQuery(query.getQuery());
        }
    }

    private String getExtraFilter(String fieldName, List<String> ids) {
        if (ids != null && !ids.isEmpty()) {
            return fieldName + ":(" + ids.stream().collect(Collectors.joining(" OR ")) + ")";
        }
        return null;
    }
}

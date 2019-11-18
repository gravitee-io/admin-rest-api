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
package io.gravitee.rest.api.service.impl;

import io.gravitee.rest.api.model.*;
import io.gravitee.rest.api.model.analytics.query.LogQuery;
import io.gravitee.rest.api.model.api.ApiEntity;
import io.gravitee.rest.api.model.log.*;
import io.gravitee.rest.api.model.log.extended.Request;
import io.gravitee.rest.api.model.log.extended.Response;
import io.gravitee.rest.api.model.parameters.Key;
import io.gravitee.rest.api.service.*;
import io.gravitee.rest.api.service.exceptions.*;
import io.gravitee.repository.analytics.AnalyticsException;
import io.gravitee.repository.analytics.query.*;
import io.gravitee.repository.analytics.query.tabular.TabularResponse;
import io.gravitee.repository.log.api.LogRepository;
import io.gravitee.repository.log.model.ExtendedLog;
import io.gravitee.repository.management.model.ApplicationStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.commons.lang3.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.gravitee.repository.log.model.Log.AuditEvent.LOG_READ;
import static io.gravitee.repository.management.model.Audit.AuditProperties.REQUEST_ID;
import static java.lang.System.lineSeparator;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class LogsServiceImpl implements LogsService {

    private final Logger logger = LoggerFactory.getLogger(LogsServiceImpl.class);

    private static final String APPLICATION_KEYLESS = "1";
    private static final String RFC_3339_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    private static final FastDateFormat dateFormatter = FastDateFormat.getInstance(RFC_3339_DATE_FORMAT);
    private static final char separator = ';';

    @Autowired
    private LogRepository logRepository;
    @Autowired
    private ApiService apiService;
    @Autowired
    private ApplicationService applicationService;
    @Autowired
    private PlanService planService;
    @Autowired
    private InstanceService instanceService;
    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private SubscriptionService subscriptionService;
    @Autowired
    private AuditService auditService;
    @Autowired
    private ParameterService parameterService;

    @Override
    public SearchLogResponse findByApi(String api, LogQuery query) {
        try {
            final String field = query.getField() == null ? "@timestamp" : query.getField();
            TabularResponse response = logRepository.query(QueryBuilders.tabular()
                    .page(query.getPage())
                    .size(query.getSize())
                    .query(query.getQuery())
                    .sort(SortBuilder.on(field, query.isOrder() ? Order.ASC : Order.DESC, null))
                    .timeRange(
                            DateRangeBuilder.between(query.getFrom(), query.getTo()),
                            IntervalBuilder.interval(query.getInterval())
                    )
                    .root("api", api)
                    .build());

            SearchLogResponse<ApiRequestItem> logResponse = new SearchLogResponse<>(response.getSize());

            // Transform repository logs
            logResponse.setLogs(response.getLogs().stream()
                    .map(this::toApiRequestItem)
                    .collect(Collectors.toList()));

            // Add metadata (only if they are results)
            if (response.getSize() > 0) {
                Map<String, Map<String, String>> metadata = new HashMap<>();

                logResponse.getLogs().forEach(logItem -> {
                    String application = logItem.getApplication();
                    String plan = logItem.getPlan();

                    if (application != null) {
                        metadata.computeIfAbsent(application, getApplicationMetadata(application));
                    }
                    if (plan != null) {
                        metadata.computeIfAbsent(plan, getPlanMetadata(plan));
                    }
                });

                logResponse.setMetadata(metadata);
            }

            return logResponse;
        } catch (AnalyticsException ae) {
            logger.error("Unable to retrieve logs: ", ae);
            throw new TechnicalManagementException("Unable to retrieve logs", ae);
        }
    }

    @Override
    public ApiRequest findApiLog(String id, Long timestamp) {
        try {
            final ExtendedLog log = logRepository.findById(id, timestamp);
            if (parameterService.findAsBoolean(Key.LOGGING_AUDIT_ENABLED)) {
                auditService.createApiAuditLog(log.getApi(),
                        Collections.singletonMap(REQUEST_ID, id),
                        LOG_READ,
                        new Date(),
                        null,
                        null);
            }
            return toApiRequest(log);
        } catch (AnalyticsException ae) {
            logger.error("Unable to retrieve log: " + id, ae);
            throw new TechnicalManagementException("Unable to retrieve log: " + id, ae);
        }
    }

    @Override
    public SearchLogResponse findByApplication(String application, LogQuery query) {
        try {
            final String field = query.getField() == null ? "@timestamp" : query.getField();
            TabularResponse response = logRepository.query(
                    QueryBuilders.tabular()
                            .page(query.getPage())
                            .size(query.getSize())
                            .query(query.getQuery())
                            .sort(SortBuilder.on(field, query.isOrder() ? Order.ASC : Order.DESC, null))
                            .timeRange(
                                    DateRangeBuilder.between(query.getFrom(), query.getTo()),
                                    IntervalBuilder.interval(query.getInterval())
                            )
                            .root("application", application)
                            .build());

            SearchLogResponse<ApplicationRequestItem> logResponse = new SearchLogResponse<>(response.getSize());

            // Transform repository logs
            logResponse.setLogs(response.getLogs().stream()
                    .map(this::toApplicationRequestItem)
                    .collect(Collectors.toList()));

            // Add metadata (only if they are results)
            if (response.getSize() > 0) {
                Map<String, Map<String, String>> metadata = new HashMap<>();

                logResponse.getLogs().forEach(logItem -> {
                    String api = logItem.getApi();
                    String plan = logItem.getPlan();

                    if (api != null) {
                        metadata.computeIfAbsent(api, getAPIMetadata(api));
                    }
                    if (plan != null) {
                        metadata.computeIfAbsent(plan, getPlanMetadata(plan));
                    }
                });

                logResponse.setMetadata(metadata);
            }

            return logResponse;
        } catch (AnalyticsException ae) {
            logger.error("Unable to retrieve logs: ", ae);
            throw new TechnicalManagementException("Unable to retrieve logs", ae);
        }
    }

    @Override
    public ApplicationRequest findApplicationLog(String id, Long timestamp) {
        try {
            return toApplicationRequest(logRepository.findById(id, timestamp));
        } catch (AnalyticsException ae) {
            logger.error("Unable to retrieve log: " + id, ae);
            throw new TechnicalManagementException("Unable to retrieve log: " + id, ae);
        }
    }

    private Function<String, Map<String, String>> getAPIMetadata(String api) {
        return s -> {
            Map<String, String> metadata = new HashMap<>();

            try {
                ApiEntity apiEntity = apiService.findById(api);
                metadata.put("name", apiEntity.getName());
                metadata.put("version", apiEntity.getVersion());
            } catch (ApiNotFoundException anfe) {
                metadata.put("name", "Deleted API");
                metadata.put("deleted", "true");
            }

            return metadata;
        };
    }

    private Function<String, Map<String, String>> getApplicationMetadata(String application) {
        return s -> {
            Map<String, String> metadata = new HashMap<>();

            try {
                ApplicationEntity applicationEntity = applicationService.findById(application);
                metadata.put("name", applicationEntity.getName());
                if (ApplicationStatus.ARCHIVED.toString().equals(applicationEntity.getStatus())) {
                    metadata.put("deleted", "true");
                }
            } catch (ApplicationNotFoundException anfe) {
                metadata.put("deleted", "true");
                if (application.equals(APPLICATION_KEYLESS)) {
                    metadata.put("name", "Unknown application (keyless)");
                } else {
                    metadata.put("name", "Deleted application");
                }
            }

            return metadata;
        };
    }

    private Function<String, Map<String, String>> getPlanMetadata(String plan) {
        return s -> {
            Map<String, String> metadata = new HashMap<>();

            try {
                PlanEntity planEntity = planService.findById(plan);
                metadata.put("name", planEntity.getName());
            } catch (PlanNotFoundException anfe) {
                metadata.put("deleted", "true");
            }

            return metadata;
        };
    }

    private Function<String, Map<String, String>> getGatewayMetadata(String gateway) {
        return s -> {
            Map<String, String> metadata = new HashMap<>();

            Optional<InstanceListItem> instanceOptional = instanceService.findInstances(true, gateway).stream().findFirst();

            if (instanceOptional.isPresent()) {
                metadata.put("hostname", instanceOptional.get().getHostname());
                metadata.put("ip", instanceOptional.get().getIp());
                if (instanceOptional.get().getTenant() != null) {
                    metadata.put("tenant", instanceOptional.get().getTenant());
                }
            } else {
                metadata.put("deleted", "true");
            }

            return metadata;
        };
    }

    private String getSubscription(io.gravitee.repository.log.model.ExtendedLog log) {
        if ("API_KEY".equals(log.getSecurityType())) {
            try {
                ApiKeyEntity key = apiKeyService.findByKey(log.getSecurityToken());
                if (key != null) {
                    return key.getSubscription();
                }
            } catch (ApiKeyNotFoundException e) {
                // wrong apikey
            }
        } else if (log.getPlan() != null && log.getApplication() != null) {
            PlanEntity plan = planService.findById(log.getPlan());
            if (!PlanSecurityType.API_KEY.equals(plan.getSecurity()) && !PlanSecurityType.KEY_LESS.equals(plan.getSecurity())) {
                Collection<SubscriptionEntity> subscriptions = subscriptionService.findByApplicationAndPlan(log.getApplication(), log.getPlan());
                if (!subscriptions.isEmpty() && subscriptions.size() == 1) {
                    return subscriptions.iterator().next().getId();
                }
            }
        }
        return null;
    }

    @Override
    public String exportAsCsv(final SearchLogResponse searchLogResponse) {
        if (searchLogResponse.getLogs() == null || searchLogResponse.getLogs().isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("Date");
        sb.append(separator);
        sb.append("Request Id");
        sb.append(separator);
        sb.append("Transaction Id");
        sb.append(separator);
        sb.append("Method");
        sb.append(separator);
        sb.append("Path");
        sb.append(separator);
        sb.append("Status");
        sb.append(separator);
        sb.append("Response Time");
        sb.append(separator);
        sb.append("Plan");
        sb.append(separator);

        //get the first item to define the type of export
        if (searchLogResponse.getLogs().get(0) instanceof ApiRequestItem) {
            sb.append("Application");
            sb.append(lineSeparator());

            for (final Object log : searchLogResponse.getLogs()) {
                final ApiRequestItem apiLog = (ApiRequestItem) log;
                sb.append(dateFormatter.format(apiLog.getTimestamp()));
                sb.append(separator);
                sb.append(apiLog.getId());
                sb.append(separator);
                sb.append(apiLog.getTransactionId());
                sb.append(separator);
                sb.append(apiLog.getMethod());
                sb.append(separator);
                sb.append(apiLog.getPath());
                sb.append(separator);
                sb.append(apiLog.getStatus());
                sb.append(separator);
                sb.append(apiLog.getResponseTime());
                sb.append(separator);
                final Object plan = searchLogResponse.getMetadata().get(apiLog.getPlan());
                sb.append(getName(plan));
                sb.append(separator);
                final Object application = searchLogResponse.getMetadata().get(apiLog.getApplication());
                sb.append(getName(application));
                sb.append(lineSeparator());
            }

        } else if (searchLogResponse.getLogs().get(0) instanceof ApplicationRequestItem) {
            sb.append("API");
            sb.append(lineSeparator());

            for (final Object log : searchLogResponse.getLogs()) {
                final ApplicationRequestItem applicationLog = (ApplicationRequestItem) log;
                sb.append(dateFormatter.format(applicationLog.getTimestamp()));
                sb.append(separator);
                sb.append(applicationLog.getId());
                sb.append(separator);
                sb.append(applicationLog.getTransactionId());
                sb.append(separator);
                sb.append(applicationLog.getMethod());
                sb.append(separator);
                sb.append(applicationLog.getPath());
                sb.append(separator);
                sb.append(applicationLog.getStatus());
                sb.append(separator);
                sb.append(applicationLog.getResponseTime());
                sb.append(separator);
                final Object plan = searchLogResponse.getMetadata().get(applicationLog.getPlan());
                sb.append(getName(plan));
                sb.append(separator);
                final Object api = searchLogResponse.getMetadata().get(applicationLog.getApi());
                sb.append(getName(api));
                sb.append(lineSeparator());
            }
        }
        return sb.toString();
    }

    private String getName(Object map) {
        return map == null ? "" : ((Map) map).get("name").toString();
    }

    private ApiRequestItem toApiRequestItem(io.gravitee.repository.log.model.Log log) {
        ApiRequestItem req = new ApiRequestItem();
        req.setId(log.getId());
        req.setTransactionId(log.getTransactionId());
        req.setApplication(log.getApplication());
        req.setMethod(log.getMethod());
        req.setPath(new QueryStringDecoder(log.getUri()).path());
        req.setPlan(log.getPlan());
        req.setResponseTime(log.getResponseTime());
        req.setStatus(log.getStatus());
        req.setTimestamp(log.getTimestamp());
        req.setEndpoint(log.getEndpoint() != null);
        req.setUser(log.getUser());
        return req;
    }

    private ApplicationRequestItem toApplicationRequestItem(io.gravitee.repository.log.model.Log log) {
        ApplicationRequestItem req = new ApplicationRequestItem();
        req.setId(log.getId());
        req.setTransactionId(log.getTransactionId());
        req.setApi(log.getApi());
        req.setMethod(log.getMethod());
        req.setPath(new QueryStringDecoder(log.getUri()).path());
        req.setPlan(log.getPlan());
        req.setResponseTime(log.getResponseTime());
        req.setStatus(log.getStatus());
        req.setTimestamp(log.getTimestamp());
        req.setUser(log.getUser());
        return req;
    }

    private ApiRequest toApiRequest(io.gravitee.repository.log.model.ExtendedLog log) {
        ApiRequest req = new ApiRequest();
        req.setId(log.getId());
        req.setTransactionId(log.getTransactionId());
        req.setApplication(log.getApplication());
        req.setApiResponseTime(log.getApiResponseTime());
        req.setEndpoint(log.getEndpoint());
        req.setLocalAddress(log.getLocalAddress());
        req.setRemoteAddress(log.getRemoteAddress());
        req.setMethod(log.getMethod());
        req.setPath(new QueryStringDecoder(log.getUri()).path());
        req.setPlan(log.getPlan());
        req.setRequestContentLength(log.getRequestContentLength());
        req.setResponseContentLength(log.getResponseContentLength());
        req.setResponseTime(log.getResponseTime());
        req.setStatus(log.getStatus());
        req.setTenant(log.getTenant());
        req.setTimestamp(log.getTimestamp());
        req.setUri(log.getUri());
        req.setMessage(log.getMessage());
        req.setGateway(log.getGateway());
        req.setSubscription(getSubscription(log));
        req.setHost(log.getHost());
        req.setSecurityType(log.getSecurityType());
        req.setSecurityToken(log.getSecurityToken());

        req.setClientRequest(createRequest(log.getClientRequest()));
        req.setProxyRequest(createRequest(log.getProxyRequest()));
        req.setClientResponse(createResponse(log.getClientResponse()));
        req.setProxyResponse(createResponse(log.getProxyResponse()));

        Map<String, Map<String, String>> metadata = new HashMap<>();

        String application = log.getApplication();
        String plan = log.getPlan();
        String gateway = log.getGateway();


        if (application != null) {
            metadata.computeIfAbsent(application, getApplicationMetadata(application));
        }
        if (plan != null) {
            metadata.computeIfAbsent(plan, getPlanMetadata(plan));
        }
        if (gateway != null) {
            metadata.computeIfAbsent(gateway, getGatewayMetadata(gateway));
        }

        req.setMetadata(metadata);
        req.setUser(log.getUser());

        return req;
    }

    private Request createRequest(io.gravitee.repository.log.model.Request repoRequest) {
        if (repoRequest == null) {
            return null;
        }

        Request request = new Request();
        request.setUri(repoRequest.getUri());
        request.setMethod(repoRequest.getMethod());
        request.setHeaders(repoRequest.getHeaders());
        request.setBody(repoRequest.getBody());

        return request;
    }

    private Response createResponse(io.gravitee.repository.log.model.Response repoResponse) {
        if (repoResponse == null) {
            return null;
        }

        Response response = new Response();
        response.setStatus(repoResponse.getStatus());
        response.setHeaders(repoResponse.getHeaders());
        response.setBody(repoResponse.getBody());

        return response;
    }

    private ApplicationRequest toApplicationRequest(io.gravitee.repository.log.model.ExtendedLog log) {
        ApplicationRequest req = new ApplicationRequest();
        req.setId(log.getId());
        req.setTransactionId(log.getTransactionId());
        req.setApi(log.getApi());
        req.setMethod(log.getMethod());
        req.setPath(new QueryStringDecoder(log.getUri()).path());
        req.setPlan(log.getPlan());
        req.setRequestContentLength(log.getRequestContentLength());
        req.setResponseContentLength(log.getResponseContentLength());
        req.setResponseTime(log.getResponseTime());
        req.setStatus(log.getStatus());
        req.setTimestamp(log.getTimestamp());
        req.setRequest(createRequest(log.getClientRequest()));
        req.setResponse(createResponse(log.getClientResponse()));
        req.setHost(log.getHost());
        req.setSecurityType(log.getSecurityType());
        req.setSecurityToken(log.getSecurityToken());

        Map<String, Map<String, String>> metadata = new HashMap<>();

        String api = log.getApi();
        String plan = log.getPlan();
        String gateway = log.getGateway();

        if (api != null) {
            metadata.computeIfAbsent(api, getAPIMetadata(api));
        }
        if (plan != null) {
            metadata.computeIfAbsent(plan, getPlanMetadata(plan));
        }
        if (gateway != null) {
            metadata.computeIfAbsent(gateway, getGatewayMetadata(gateway));
        }

        req.setMetadata(metadata);
        req.setUser(log.getUser());

        return req;
    }
}

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
package io.gravitee.rest.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import io.gravitee.definition.jackson.datatype.GraviteeMapper;
import io.gravitee.definition.model.Endpoint;
import io.gravitee.definition.model.EndpointGroup;
import io.gravitee.definition.model.Proxy;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.ApiRepository;
import io.gravitee.repository.management.api.MembershipRepository;
import io.gravitee.repository.management.api.search.ApiCriteria;
import io.gravitee.repository.management.model.*;
import io.gravitee.rest.api.model.api.ApiEntity;
import io.gravitee.rest.api.model.api.UpdateApiEntity;
import io.gravitee.rest.api.model.parameters.Key;
import io.gravitee.rest.api.model.permissions.SystemRole;
import io.gravitee.rest.api.service.AuditService;
import io.gravitee.rest.api.service.ParameterService;
import io.gravitee.rest.api.service.TagService;
import io.gravitee.rest.api.service.UserService;
import io.gravitee.rest.api.service.WorkflowService;
import io.gravitee.rest.api.service.exceptions.*;
import io.gravitee.rest.api.service.impl.ApiServiceImpl;
import io.gravitee.rest.api.service.jackson.filter.ApiPermissionFilter;
import io.gravitee.rest.api.service.search.SearchEngineService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.internal.util.collections.Sets;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Optional;

import static io.gravitee.rest.api.model.WorkflowReferenceType.API;
import static io.gravitee.rest.api.model.WorkflowType.REVIEW;
import static io.gravitee.rest.api.model.api.ApiLifecycleState.*;
import static java.util.Collections.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.internal.util.collections.Sets.newSet;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 */
@RunWith(MockitoJUnitRunner.class)
public class ApiService_UpdateTest {

    private static final String API_ID = "id-api";
    private static final String API_ID2 = "id-api2";
    private static final String API_NAME = "myAPI";
    private static final String USER_NAME = "myUser";

    @InjectMocks
    private ApiServiceImpl apiService = new ApiServiceImpl();

    @Mock
    private ApiRepository apiRepository;
    @Mock
    private MembershipRepository membershipRepository;
    @Spy
    private ObjectMapper objectMapper = new GraviteeMapper();
    @Mock
    private UpdateApiEntity existingApi;
    @Mock
    private Api api;
    @Mock
    private UserService userService;
    @Mock
    private AuditService auditService;
    @Mock
    private SearchEngineService searchEngineService;
    @Mock
    private TagService tagService;
    @Mock
    private ParameterService parameterService;
    @Mock
    private WorkflowService workflowService;

    @Before
    public void setUp() {
        PropertyFilter apiMembershipTypeFilter = new ApiPermissionFilter();
        objectMapper.setFilterProvider(new SimpleFilterProvider(Collections.singletonMap("apiMembershipTypeFilter", apiMembershipTypeFilter)));

        final SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(mock(Authentication.class));
        SecurityContextHolder.setContext(securityContext);

        when(api.getId()).thenReturn(API_ID);
        when(api.getDefinition()).thenReturn("{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"}}");
    }

    @Test
    public void shouldUpdate() throws TechnicalException {
        prepareUpdate();

        final ApiEntity apiEntity = apiService.update(API_ID, existingApi);

        assertNotNull(apiEntity);
        assertEquals(API_NAME, apiEntity.getName());
    }

    @Test(expected = ApiNotFoundException.class)
    public void shouldNotUpdateBecauseNotFound() throws TechnicalException {
        when(apiRepository.findById(API_ID)).thenReturn(Optional.empty());

        apiService.update(API_ID, existingApi);
    }

    @Test(expected = TechnicalManagementException.class)
    public void shouldNotUpdateBecauseTechnicalException() throws TechnicalException {
        when(existingApi.getName()).thenReturn(API_NAME);
        when(existingApi.getVersion()).thenReturn("v1");
        when(existingApi.getDescription()).thenReturn("Ma description");
        final Proxy proxy = mock(Proxy.class);
        when(existingApi.getProxy()).thenReturn(proxy);
        when(proxy.getContextPath()).thenReturn("/context");
        when(existingApi.getLifecycleState()).thenReturn(CREATED);

        when(apiRepository.findById(API_ID)).thenReturn(Optional.of(api));
        when(api.getApiLifecycleState()).thenReturn(ApiLifecycleState.CREATED);
        when(apiRepository.update(any())).thenThrow(TechnicalException.class);

        apiService.update(API_ID, existingApi);
    }

    @Test
    public void shouldUpdateForUserBecauseContextPathNotExists() throws TechnicalException {
        testUpdateWithContextPath("/context", "/context2");
    }

    @Test(expected = ApiContextPathAlreadyExistsException.class)
    public void shouldNotUpdateForUserBecauseContextPathExists() throws TechnicalException {
        testUpdateWithContextPath("/context", "/context");
    }

    @Test(expected = ApiContextPathAlreadyExistsException.class)
    public void shouldNotUpdateForUserBecauseSubContextPathExists() throws TechnicalException {
        testUpdateWithContextPath("/context/toto", "/context");
    }

    @Test(expected = ApiContextPathAlreadyExistsException.class)
    public void shouldNotUpdateForUserBecauseSubContextPathExists2() throws TechnicalException {
        testUpdateWithContextPath("/context", "/context/toto");
    }

    @Test(expected = ApiContextPathAlreadyExistsException.class)
    public void shouldNotUpdateForUserBecauseContextPathExistsWithSlash() throws TechnicalException {
        testUpdateWithContextPath("/context", "/context/");
    }

    @Test(expected = ApiContextPathAlreadyExistsException.class)
    public void shouldNotUpdateForUserBecauseSubContextPathExistsWithSlash() throws TechnicalException {
        testUpdateWithContextPath("/context/toto", "/context/");
    }

    @Test(expected = ApiContextPathAlreadyExistsException.class)
    public void shouldNotUpdateForUserBecauseSubContextPathExists2WithSlash() throws TechnicalException {
        testUpdateWithContextPath("/context", "/context/toto/");
    }

    private void testUpdateWithContextPath(String existingContextPath, String contextPathToCreate) throws TechnicalException {
        when(apiRepository.findById(API_ID)).thenReturn(Optional.of(api));
        when(apiRepository.update(any())).thenReturn(api);
        when(api.getId()).thenReturn(API_ID2);
        when(api.getName()).thenReturn(API_NAME);
        when(api.getApiLifecycleState()).thenReturn(ApiLifecycleState.CREATED);

        when(existingApi.getName()).thenReturn(API_NAME);
        when(existingApi.getVersion()).thenReturn("v1");
        when(existingApi.getDescription()).thenReturn("Ma description");
        final Proxy proxy = mock(Proxy.class);
        when(existingApi.getProxy()).thenReturn(proxy);
        when(proxy.getContextPath()).thenReturn(contextPathToCreate);
        when(existingApi.getLifecycleState()).thenReturn(CREATED);

        when(apiRepository.search(new ApiCriteria.Builder().environment("DEFAULT").build())).thenReturn(singletonList(api));
        when(api.getDefinition()).thenReturn("{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"" + existingContextPath + "\"}}");

        Membership po2 = new Membership("admin", API_ID2, MembershipReferenceType.API);
        po2.setRoles(Collections.singletonMap(RoleScope.API.getId(), SystemRole.PRIMARY_OWNER.name()));
        when(membershipRepository.findByReferencesAndRole(
                MembershipReferenceType.API,
                singletonList(API_ID2),
                RoleScope.API,
                SystemRole.PRIMARY_OWNER.name()))
                .thenReturn(Collections.singleton(po2));

        apiService.update(API_ID, existingApi);
    }

    @Test(expected = EndpointNameInvalidException.class)
    public void shouldNotUpdateWithInvalidEndpointGroupName() throws TechnicalException {
        when(apiRepository.findById(API_ID)).thenReturn(Optional.of(api));
        when(api.getId()).thenReturn(API_ID2);
        when(api.getName()).thenReturn(API_NAME);

        final Proxy proxy = mock(Proxy.class);
        when(existingApi.getProxy()).thenReturn(proxy);
        when(proxy.getContextPath()).thenReturn("/new");
        final EndpointGroup group = mock(EndpointGroup.class);
        when(group.getName()).thenReturn("inva:lid");
        when(proxy.getGroups()).thenReturn(singleton(group));

        when(apiRepository.search(new ApiCriteria.Builder().environment("DEFAULT").build())).thenReturn(singletonList(api));
        when(api.getDefinition()).thenReturn("{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"}}");

        apiService.update(API_ID, existingApi);

        fail("should throw EndpointNameInvalidException");
    }

    @Test(expected = EndpointNameInvalidException.class)
    public void shouldNotUpdateWithInvalidEndpointName() throws TechnicalException {
        when(apiRepository.findById(API_ID)).thenReturn(Optional.of(api));
        when(api.getId()).thenReturn(API_ID2);
        when(api.getName()).thenReturn(API_NAME);

        final Proxy proxy = mock(Proxy.class);
        when(existingApi.getProxy()).thenReturn(proxy);
        when(proxy.getContextPath()).thenReturn("/new");
        final EndpointGroup group = mock(EndpointGroup.class);
        when(group.getName()).thenReturn("group");
        when(proxy.getGroups()).thenReturn(singleton(group));
        Endpoint endpoint = mock(Endpoint.class);
        when(endpoint.getName()).thenReturn("inva:lid");
        when(group.getEndpoints()).thenReturn(singleton(endpoint));

        when(apiRepository.search(new ApiCriteria.Builder().environment("DEFAULT").build())).thenReturn(singletonList(api));
        when(api.getDefinition()).thenReturn("{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"}}");

        apiService.update(API_ID, existingApi);

        fail("should throw EndpointNameInvalidException");
    }

    private void prepareUpdate() throws TechnicalException {
        when(apiRepository.findById(API_ID)).thenReturn(Optional.of(api));
        when(apiRepository.update(any())).thenReturn(api);
        when(api.getName()).thenReturn(API_NAME);
        when(api.getApiLifecycleState()).thenReturn(ApiLifecycleState.CREATED);

        when(existingApi.getName()).thenReturn(API_NAME);
        when(existingApi.getVersion()).thenReturn("v1");
        when(existingApi.getDescription()).thenReturn("Ma description");
        final Proxy proxy = mock(Proxy.class);
        when(existingApi.getProxy()).thenReturn(proxy);
        when(existingApi.getLifecycleState()).thenReturn(CREATED);
        when(proxy.getContextPath()).thenReturn("/context");
        Membership po = new Membership(USER_NAME, API_ID, MembershipReferenceType.API);
        po.setRoles(Collections.singletonMap(RoleScope.API.getId(), SystemRole.PRIMARY_OWNER.name()));
        when(membershipRepository.findByReferencesAndRole(any(), any(), any(), any()))
                .thenReturn(Collections.singleton(po));
    }

    @Test
    public void shouldUpdateWithAllowedTag() throws TechnicalException {
        prepareUpdate();
        when(existingApi.getTags()).thenReturn(singleton("public"));
        when(api.getDefinition()).thenReturn("{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"} ,\"tags\": [\"public\"]}");
        final ApiEntity apiEntity = apiService.update(API_ID, existingApi);
        assertNotNull(apiEntity);
        assertEquals(API_NAME, apiEntity.getName());
    }

    @Test
    public void shouldUpdateWithExistingAllowedTag() throws TechnicalException {
        prepareUpdate();
        when(existingApi.getTags()).thenReturn(singleton("private"));
        when(api.getDefinition()).thenReturn("{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"} ,\"tags\": [\"public\"]}");
        when(tagService.findByUser(any())).thenReturn(Sets.newSet("public", "private"));
        final ApiEntity apiEntity = apiService.update(API_ID, existingApi);
        assertNotNull(apiEntity);
        assertEquals(API_NAME, apiEntity.getName());
    }

    @Test
    public void shouldUpdateWithExistingAllowedTags() throws TechnicalException {
        prepareUpdate();
        when(existingApi.getTags()).thenReturn(newSet("public", "private"));
        when(api.getDefinition()).thenReturn("{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"} ,\"tags\": [\"public\"]}");
        when(tagService.findByUser(any())).thenReturn(Sets.newSet("public", "private"));
        final ApiEntity apiEntity = apiService.update(API_ID, existingApi);
        assertNotNull(apiEntity);
        assertEquals(API_NAME, apiEntity.getName());
    }

    @Test
    public void shouldUpdateWithExistingNotAllowedTag() throws TechnicalException {
        prepareUpdate();
        when(existingApi.getTags()).thenReturn(newSet("public", "private"));
        when(api.getDefinition()).thenReturn("{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"} ,\"tags\": [\"public\", \"private\"]}");
        final ApiEntity apiEntity = apiService.update(API_ID, existingApi);
        assertNotNull(apiEntity);
        assertEquals(API_NAME, apiEntity.getName());
    }

    @Test(expected = TagNotAllowedException.class)
    public void shouldNotUpdateWithNotAllowedTag() throws TechnicalException {
        when(apiRepository.findById(API_ID)).thenReturn(Optional.of(api));
        when(api.getDefinition()).thenReturn("{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"} ,\"tags\": [\"public\"]}");
        when(existingApi.getTags()).thenReturn(singleton("private"));
        final Proxy proxy = mock(Proxy.class);
        when(existingApi.getProxy()).thenReturn(proxy);
        when(proxy.getContextPath()).thenReturn("/context");
        when(tagService.findByUser(any())).thenReturn(emptySet());
        apiService.update(API_ID, existingApi);
    }

    @Test(expected = TagNotAllowedException.class)
    public void shouldNotUpdateWithExistingNotAllowedTag() throws TechnicalException {
        when(apiRepository.findById(API_ID)).thenReturn(Optional.of(api));
        when(api.getDefinition()).thenReturn("{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"} ,\"tags\": [\"public\"]}");
        when(existingApi.getTags()).thenReturn(singleton("private"));
        final Proxy proxy = mock(Proxy.class);
        when(existingApi.getProxy()).thenReturn(proxy);
        when(proxy.getContextPath()).thenReturn("/context");
        when(tagService.findByUser(any())).thenReturn(singleton("public"));
        apiService.update(API_ID, existingApi);
    }

    @Test(expected = TagNotAllowedException.class)
    public void shouldNotUpdateWithExistingNotAllowedTags() throws TechnicalException {
        when(apiRepository.findById(API_ID)).thenReturn(Optional.of(api));
        when(api.getDefinition()).thenReturn("{\"id\": \"" + API_ID + "\",\"name\": \"" + API_NAME + "\",\"proxy\": {\"context_path\": \"/old\"} ,\"tags\": [\"public\", \"private\"]}");
        when(existingApi.getTags()).thenReturn(emptySet());
        final Proxy proxy = mock(Proxy.class);
        when(existingApi.getProxy()).thenReturn(proxy);
        when(proxy.getContextPath()).thenReturn("/context");
        when(tagService.findByUser(any())).thenReturn(singleton("private"));
        apiService.update(API_ID, existingApi);
    }

    @Test
    public void shouldPublishApi() throws TechnicalException {
        prepareUpdate();
        // from UNPUBLISHED state
        when(existingApi.getLifecycleState()).thenReturn(UNPUBLISHED);
        when(api.getApiLifecycleState()).thenReturn(ApiLifecycleState.PUBLISHED);
        ApiEntity apiEntity = apiService.update(API_ID, existingApi);
        assertNotNull(apiEntity);
        assertEquals(io.gravitee.rest.api.model.api.ApiLifecycleState.PUBLISHED, apiEntity.getLifecycleState());
        // from CREATED state
        when(existingApi.getLifecycleState()).thenReturn(CREATED);
        when(api.getApiLifecycleState()).thenReturn(ApiLifecycleState.PUBLISHED);
        apiEntity = apiService.update(API_ID, existingApi);
        assertNotNull(apiEntity);
        assertEquals(io.gravitee.rest.api.model.api.ApiLifecycleState.PUBLISHED, apiEntity.getLifecycleState());
    }

    @Test
    public void shouldUnpublishApi() throws TechnicalException {
        prepareUpdate();
        when(existingApi.getLifecycleState()).thenReturn(io.gravitee.rest.api.model.api.ApiLifecycleState.PUBLISHED);
        when(api.getApiLifecycleState()).thenReturn(ApiLifecycleState.UNPUBLISHED);
        final ApiEntity apiEntity = apiService.update(API_ID, existingApi);
        assertNotNull(apiEntity);
        assertEquals(UNPUBLISHED, apiEntity.getLifecycleState());
    }

    @Test
    public void shouldNotChangeLifecycleStateFromUnpublishedToCreated() throws TechnicalException {
        prepareUpdate();
        assertUpdate(ApiLifecycleState.UNPUBLISHED, CREATED, true);
        assertUpdate(ApiLifecycleState.UNPUBLISHED, PUBLISHED, false);
        assertUpdate(ApiLifecycleState.UNPUBLISHED, UNPUBLISHED, false);
        assertUpdate(ApiLifecycleState.UNPUBLISHED, ARCHIVED, false);
    }

    @Test
    public void shouldOnlyUnpublishADeprecatedApi() throws TechnicalException {
        prepareUpdate();
        assertUpdate(ApiLifecycleState.DEPRECATED, CREATED, true);
        assertUpdate(ApiLifecycleState.DEPRECATED, PUBLISHED, true);
        assertUpdate(ApiLifecycleState.DEPRECATED, UNPUBLISHED, false);
        assertUpdate(ApiLifecycleState.DEPRECATED, ARCHIVED, true);
    }

    @Test
    public void shouldNotChangeLifecycleStateFromArchived() throws TechnicalException {
        prepareUpdate();
        assertUpdate(ApiLifecycleState.ARCHIVED, CREATED, true);
        assertUpdate(ApiLifecycleState.ARCHIVED, PUBLISHED, true);
        assertUpdate(ApiLifecycleState.ARCHIVED, UNPUBLISHED, true);
        assertUpdate(ApiLifecycleState.ARCHIVED, DEPRECATED, true);
    }

    @Test
    public void shouldNotChangeLifecycleStateFromCreatedInReview() throws TechnicalException {
        prepareUpdate();
        when(parameterService.findAsBoolean(Key.API_REVIEW_ENABLED)).thenReturn(true);
        final Workflow workflow = new Workflow();
        workflow.setState("IN_REVIEW");
        when(workflowService.findByReferenceAndType(API, API_ID, REVIEW)).thenReturn(singletonList(workflow));

        assertUpdate(ApiLifecycleState.CREATED, CREATED, false);
        assertUpdate(ApiLifecycleState.CREATED, PUBLISHED, true);
        assertUpdate(ApiLifecycleState.CREATED, UNPUBLISHED, true);
        assertUpdate(ApiLifecycleState.CREATED, DEPRECATED, true);
    }

    private void assertUpdate(final ApiLifecycleState fromLifecycleState,
                              final io.gravitee.rest.api.model.api.ApiLifecycleState lifecycleState, final boolean shouldFail) {
        when(api.getApiLifecycleState()).thenReturn(fromLifecycleState);
        when(existingApi.getLifecycleState()).thenReturn(lifecycleState);
        boolean failed = false;
        try {
            apiService.update(API_ID, existingApi);
        } catch (final LifecycleStateChangeNotAllowedException ise) {
            failed = true;
        }
        if (!failed && shouldFail) {
            fail("Should not be possible to change the lifecycle state of a " + fromLifecycleState + " API to " + lifecycleState);
        }
    }
}

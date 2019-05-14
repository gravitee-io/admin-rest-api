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

import io.gravitee.common.component.Lifecycle;
import io.gravitee.definition.model.Proxy;
import io.gravitee.management.model.Visibility;
import io.gravitee.management.model.api.ApiEntity;
import io.gravitee.management.model.api.UpdateApiEntity;
import io.gravitee.management.rest.resource.param.LifecycleActionParam;
import io.gravitee.management.service.exceptions.ApiNotFoundException;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.Date;

import static io.gravitee.common.http.HttpStatusCode.*;
import static java.util.Base64.getEncoder;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author David BRASSELY (brasseld at gmail.com)
 */
public class ApiResourceTest extends AbstractResourceTest {

    private static final String API = "my-api";
    private static final String UNKNOWN_API = "unknown";

    protected String contextPath() {
        return "apis/";
    }

    private ApiEntity mockApi;
    private UpdateApiEntity updateApiEntity;

    @Before
    public void init() {
        mockApi = new ApiEntity();
        mockApi.setId(API);
        mockApi.setName(API);
        mockApi.setProxy(new Proxy());
        mockApi.setUpdatedAt(new Date());
        doReturn(mockApi).when(apiService).findById(API);
        doThrow(ApiNotFoundException.class).when(apiService).findById(UNKNOWN_API);

        updateApiEntity = new UpdateApiEntity();
        updateApiEntity.setDescription("toto");
        updateApiEntity.setVisibility(Visibility.PUBLIC);
        updateApiEntity.setName(API);
        updateApiEntity.setVersion("v1");
        updateApiEntity.setProxy(new Proxy());
        doReturn(mockApi).when(apiService).update(eq(API), any());
    }

    @Test
    public void shouldGetApi() {
        final Response response = target(API).request().get();

        assertEquals(OK_200, response.getStatus());

        final ApiEntity responseApi = response.readEntity(ApiEntity.class);
        assertNotNull(responseApi);
        assertEquals(API, responseApi.getName());
    }

    @Test
    public void shouldNotGetApiBecauseNotFound() {
        final Response response = target(UNKNOWN_API).request().get();

        assertEquals(NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldStartApi() {
        mockApi.setState(Lifecycle.State.STOPPED);
        doReturn(mockApi).when(apiService).start(eq(API), any());

        final Response response = target(API).queryParam("action", LifecycleActionParam.LifecycleAction.START).request().post(null);

        assertEquals(NO_CONTENT_204, response.getStatus());

        verify(apiService).start(API, "UnitTests");
    }

    @Test
    public void shouldNotStartApiBecauseNotFound() {
        mockApi.setState(Lifecycle.State.STOPPED);
        doReturn(mockApi).when(apiService).start(eq(API), any());
        final Response response = target(UNKNOWN_API).queryParam("action", LifecycleActionParam.LifecycleAction.START).request().post(null);

        assertEquals(NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldStopApi() {
        mockApi.setState(Lifecycle.State.STARTED);
        doReturn(mockApi).when(apiService).stop(eq(API), any());

        final Response response = target(API).queryParam("action", LifecycleActionParam.LifecycleAction.STOP).request().post(null);

        assertEquals(NO_CONTENT_204, response.getStatus());
    }

    @Test
    public void shouldNotStopApiBecauseNotFound() {
        final Response response = target(UNKNOWN_API).queryParam("action", LifecycleActionParam.LifecycleAction.STOP).request().post(null);

        assertEquals(NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldUpdateApi() {
        final Response response = target(API).request().put(Entity.json(updateApiEntity));

        assertEquals(response.readEntity(String.class), OK_200, response.getStatus());
    }

    @Test
    public void shouldNotUpdateApiBecauseTooLargePicture() {
        updateApiEntity.setPicture(randomAlphanumeric(100_000));
        final Response response = target(API).request().put(Entity.json(updateApiEntity));

        assertEquals(INTERNAL_SERVER_ERROR_500, response.getStatus());
        final String message = response.readEntity(String.class);
        assertTrue(message, message.contains("The image is too big"));
    }

    @Test
    public void shouldNotUpdateApiBecauseNotAValidImage() {
        updateApiEntity.setPicture(getEncoder().encodeToString("<script>alert('XSS')</script>".getBytes()));
        final Response response = target(API).request().put(Entity.json(updateApiEntity));

        assertEquals(INTERNAL_SERVER_ERROR_500, response.getStatus());
        final String message = response.readEntity(String.class);
        assertTrue(message, message.contains("The image is not in a valid format"));
    }
}

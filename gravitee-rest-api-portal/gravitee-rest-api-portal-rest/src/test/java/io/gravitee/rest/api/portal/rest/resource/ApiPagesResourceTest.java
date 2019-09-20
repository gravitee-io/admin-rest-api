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

import static io.gravitee.common.http.HttpStatusCode.NOT_FOUND_404;
import static io.gravitee.common.http.HttpStatusCode.OK_200;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Response;

import org.junit.Before;
import org.junit.Test;

import io.gravitee.rest.api.model.PageEntity;
import io.gravitee.rest.api.model.api.ApiEntity;
import io.gravitee.rest.api.portal.rest.model.Data;
import io.gravitee.rest.api.portal.rest.model.DatasResponse;
import io.gravitee.rest.api.portal.rest.model.Error;
import io.gravitee.rest.api.portal.rest.model.Page;

/**
 * @author Florent CHAMFROY (florent.chamfroy at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApiPagesResourceTest extends AbstractResourceTest {

    private static final String API = "my-api";

    protected String contextPath() {
        return "apis/";
    }

    @Before
    public void init() throws IOException {
        resetAllMocks();
        
        ApiEntity mockApi = new ApiEntity();
        mockApi.setId(API);
        doReturn(mockApi).when(apiService).findById(API);
        
        Set<ApiEntity> mockApis = new HashSet<>(Arrays.asList(mockApi));
        doReturn(mockApis).when(apiService).findPublishedByUser(any());

        doReturn(Arrays.asList(new PageEntity())).when(pageService).search(any());
        
        doReturn(new Page()).when(pageMapper).convert(any());
    }

    @Test
    public void shouldNotFoundWhileGettingApiPages() {
        //init
        ApiEntity userApi = new ApiEntity();
        userApi.setId("1");
        Set<ApiEntity> mockApis = new HashSet<>(Arrays.asList(userApi));
        doReturn(mockApis).when(apiService).findPublishedByUser(any());
        
        //test
        final Response response = target(API).path("pages").request().get();
        assertEquals(NOT_FOUND_404, response.getStatus());
        
        final Error error = response.readEntity(Error.class);
        assertNotNull(error);
        assertEquals("404", error.getCode());
        assertEquals("io.gravitee.rest.api.service.exceptions.ApiNotFoundException", error.getTitle());
        assertEquals("Api ["+API+"] can not be found.", error.getDetail());
    }

    @Test
    public void shouldGetApiPages() {
        doReturn(true).when(groupService).isUserAuthorizedToAccessApiData(any(), any(), any());
        doReturn(true).when(pageService).isDisplayable(any(), any(Boolean.class).booleanValue(), any());        
        
        final Response response = target(API).path("pages").request().get();
        assertEquals(OK_200, response.getStatus());

        DatasResponse pagesResponse = response.readEntity(DatasResponse.class);

        List<Data> pages = pagesResponse.getData();
        assertNotNull(pages);
        assertEquals(1, pages.size());
    }
    
    @Test
    public void shouldGetNoApiPage() {
        final Builder request = target(API).path("pages").request();
        
        // case 1
        doReturn(false).when(groupService).isUserAuthorizedToAccessApiData(any(), any(), any());
        doReturn(true).when(pageService).isDisplayable(any(), any(Boolean.class).booleanValue(), any());        
        
        Response response = request.get();
        assertEquals(OK_200, response.getStatus());

        DatasResponse pagesResponse = response.readEntity(DatasResponse.class);
        List<Data> pages = pagesResponse.getData();
        assertNotNull(pages);
        assertEquals(0, pages.size());
        
        // case 2
        doReturn(true).when(groupService).isUserAuthorizedToAccessApiData(any(), any(), any());
        doReturn(false).when(pageService).isDisplayable(any(), any(Boolean.class).booleanValue(), any());        
        
        response = request.get();
        assertEquals(OK_200, response.getStatus());

        pagesResponse = response.readEntity(DatasResponse.class);
        pages = pagesResponse.getData();
        assertNotNull(pages);
        assertEquals(0, pages.size());
        
        // case 3
        doReturn(false).when(groupService).isUserAuthorizedToAccessApiData(any(), any(), any());
        doReturn(false).when(pageService).isDisplayable(any(), any(Boolean.class).booleanValue(), any());        
        
        response = request.get();
        assertEquals(OK_200, response.getStatus());

        pagesResponse = response.readEntity(DatasResponse.class);
        pages = pagesResponse.getData();
        assertNotNull(pages);
        assertEquals(0, pages.size());
    }
}

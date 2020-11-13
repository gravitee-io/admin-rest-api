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
package io.gravitee.management.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.gravitee.common.http.MediaType;
import io.gravitee.management.model.NewPageEntity;
import io.gravitee.management.model.PageEntity;
import io.gravitee.management.model.PageSourceEntity;
import io.gravitee.management.service.exceptions.PageContentUnsafeException;
import io.gravitee.management.service.exceptions.TechnicalManagementException;
import io.gravitee.management.service.exceptions.UrlForbiddenException;
import io.gravitee.management.service.impl.PageServiceImpl;
import io.gravitee.management.service.search.SearchEngineService;
import io.gravitee.management.service.spring.ImportConfiguration;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.PageRepository;
import io.gravitee.repository.management.model.Page;
import io.gravitee.repository.management.model.PageType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class PageService_CreateTest {

    private static final String API_ID = "myAPI";
    private static final String PAGE_ID = "ba01aef0-e3da-4499-81ae-f0e3daa4995a";

    @InjectMocks
    private PageServiceImpl pageService = new PageServiceImpl();

    @Mock
    private PageRepository pageRepository;

    @Mock
    private NewPageEntity newPage;
    @Mock
    private Page page1;

    @Mock
    private AuditService auditService;

    @Mock
    private SearchEngineService searchEngineService;

    @Mock
    private ImportConfiguration importConfiguration;

    private PageEntity getPage(String resource, String contentType) throws IOException {
        URL url = Resources.getResource(resource);
        String descriptor = Resources.toString(url, Charsets.UTF_8);
        PageEntity pageEntity = new PageEntity();
        pageEntity.setContent(descriptor);
        pageEntity.setContentType(contentType);
        return pageEntity;
    }

    @Test
    public void shouldCreatePage() throws TechnicalException, IOException {
        final String name = "MARKDOWN";
        final String contrib = "contrib";
        final String content = getPage("io/gravitee/management/service/swagger-v1.json", MediaType.APPLICATION_JSON).getContent();
        final String type = "MARKDOWN";

        when(page1.getId()).thenReturn(PAGE_ID);
        when(page1.getName()).thenReturn(name);
        when(page1.getApi()).thenReturn(API_ID);
        when(page1.getType()).thenReturn(PageType.valueOf(type));
        when(page1.getLastContributor()).thenReturn(contrib);
        when(page1.getOrder()).thenReturn(1);
        when(page1.getContent()).thenReturn(content);

        when(pageRepository.create(any())).thenReturn(page1);


        when(newPage.getName()).thenReturn(name);
        when(newPage.getOrder()).thenReturn(1);
        when(newPage.getContent()).thenReturn(content);
        when(newPage.getLastContributor()).thenReturn(contrib);
        when(newPage.getType()).thenReturn(io.gravitee.management.model.PageType.SWAGGER);

        final PageEntity createdPage = pageService.createPage(API_ID, newPage);

        verify(pageRepository).create(argThat(pageToCreate -> pageToCreate.getId().split("-").length == 5 &&
            API_ID.equals(pageToCreate.getApi()) &&
            name.equals(pageToCreate.getName()) &&
            contrib.equals(pageToCreate.getLastContributor()) &&
            content.equals(pageToCreate.getContent()) &&
            io.gravitee.management.model.PageType.SWAGGER.name().equals(pageToCreate.getType().name()) &&
            pageToCreate.getCreatedAt() != null &&
            pageToCreate.getUpdatedAt() != null &&
            pageToCreate.getCreatedAt().equals(pageToCreate.getUpdatedAt())));
        assertNotNull(createdPage);
        assertEquals(5, createdPage.getId().split("-").length);
        assertEquals(1, createdPage.getOrder());
        assertEquals(content, createdPage.getContent());
        assertEquals(contrib, createdPage.getLastContributor());
        assertEquals(type, createdPage.getType());
    }

    @Test(expected = TechnicalManagementException.class)
    public void shouldNotCreateBecauseTechnicalException() throws TechnicalException, IOException {
        final String name = "PAGE_NAME";
        when(newPage.getName()).thenReturn(name);
        when(newPage.getType()).thenReturn(io.gravitee.management.model.PageType.SWAGGER);
        when(newPage.getContent()).thenReturn(
                getPage("io/gravitee/management/service/swagger-v1.json", MediaType.APPLICATION_JSON).getContent()
        );

        when(pageRepository.create(any(Page.class))).thenThrow(TechnicalException.class);

        pageService.createPage(API_ID, newPage);

        verify(pageRepository, never()).create(any());
    }

    @Test(expected = UrlForbiddenException.class)
    public void shouldNotCreateBecauseUrlForbiddenException() throws TechnicalException {

        PageSourceEntity pageSource = new PageSourceEntity();
        pageSource.setType("HTTP");
        pageSource.setConfiguration(JsonNodeFactory.instance.objectNode().put("url", "http://localhost"));

        final String name = "PAGE_NAME";

        when(newPage.getName()).thenReturn(name);
        when(newPage.getSource()).thenReturn(pageSource);

        when(importConfiguration.isAllowImportFromPrivate()).thenReturn(false);
        when(importConfiguration.getImportWhitelist()).thenReturn(Collections.emptyList());

        pageService.createPage(API_ID, newPage);

        verify(pageRepository, never()).create(any());
    }

    @Test(expected = PageContentUnsafeException.class)
    public void shouldNotCreateBecausePageContentUnsafeException() throws TechnicalException {

        setField(pageService, "markdownSanitize", true);

        final String name = "MARKDOWN";
        final String contrib = "contrib";
        final String content = "<script />";

        when(newPage.getName()).thenReturn(name);
        when(newPage.getOrder()).thenReturn(1);
        when(newPage.getContent()).thenReturn(content);
        when(newPage.getLastContributor()).thenReturn(contrib);
        when(newPage.getType()).thenReturn(io.gravitee.management.model.PageType.MARKDOWN);

        this.pageService.createPage(API_ID, newPage);

        verify(pageRepository, never()).create(any());
    }
}

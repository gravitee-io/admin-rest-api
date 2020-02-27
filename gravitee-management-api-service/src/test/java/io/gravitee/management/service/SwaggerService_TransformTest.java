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

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.gravitee.common.http.MediaType;
import io.gravitee.management.model.PageEntity;
import io.gravitee.management.service.impl.SwaggerServiceImpl;
import io.gravitee.management.service.impl.swagger.transformer.page.PageConfigurationOAITransformer;
import io.gravitee.management.service.impl.swagger.transformer.page.PageConfigurationSwaggerV2Transformer;
import io.gravitee.management.service.swagger.OAIDescriptor;
import io.gravitee.management.service.swagger.SwaggerDescriptor;
import io.gravitee.management.service.swagger.SwaggerV1Descriptor;
import io.gravitee.management.service.swagger.SwaggerV2Descriptor;
import io.swagger.models.Swagger;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class SwaggerService_TransformTest {

    private SwaggerService swaggerService;

    @Before
    public void setUp() {
        swaggerService = new SwaggerServiceImpl();
    }


    private PageEntity getPage(String resource, String contentType) throws IOException {
        URL url = Resources.getResource(resource);
        String descriptor = Resources.toString(url, Charsets.UTF_8);
        PageEntity pageEntity = new PageEntity();
        pageEntity.setContent(descriptor);
        pageEntity.setContentType(contentType);
        Map<String, String> pageConfigurationEntity = new HashMap<>();
        pageConfigurationEntity.put("tryIt", "true");
        pageConfigurationEntity.put("tryItURL", "https://my.domain.com/v1");
        pageEntity.setConfiguration(pageConfigurationEntity);
        return pageEntity;
    }

    @Test
    public void shouldTransformAPIFromSwaggerV1_json() throws IOException {
        PageEntity pageEntity = getPage("io/gravitee/management/service/swagger-v1.json", MediaType.APPLICATION_JSON);

        SwaggerV1Descriptor descriptor = (SwaggerV1Descriptor) swaggerService.parse(pageEntity.getContent());

        swaggerService.transform(descriptor,
                Collections.singleton(new PageConfigurationSwaggerV2Transformer(pageEntity)));

        assertNotNull(descriptor.toJson());
        validateV2(Json.mapper().readTree(descriptor.toJson()));
    }

    @Test
    public void shouldTransformAPIFromSwaggerV2_json() throws IOException {
        PageEntity pageEntity = getPage("io/gravitee/management/service/swagger-v2.json", MediaType.APPLICATION_JSON);

        SwaggerV2Descriptor descriptor = (SwaggerV2Descriptor) swaggerService.parse(pageEntity.getContent());

        swaggerService.transform(descriptor,
                Collections.singleton(new PageConfigurationSwaggerV2Transformer(pageEntity)));

        assertNotNull(descriptor.toJson());
        validateV2(Json.mapper().readTree(descriptor.toJson()));
    }

    @Test
    public void shouldTransformAPIFromSwaggerV2_yaml() throws IOException {
        PageEntity pageEntity = getPage("io/gravitee/management/service/swagger-v2.yaml", MediaType.TEXT_PLAIN);

        SwaggerV2Descriptor descriptor = (SwaggerV2Descriptor) swaggerService.parse(pageEntity.getContent());

        swaggerService.transform(descriptor,
                Collections.singleton(new PageConfigurationSwaggerV2Transformer(pageEntity)));

        assertNotNull(descriptor.toYaml());
        validateV2(Yaml.mapper().readTree(descriptor.toYaml()));
    }

    @Test
    public void shouldTransformAPIFromSwaggerV3_json() throws IOException {
        PageEntity pageEntity = getPage("io/gravitee/management/service/openapi.json", MediaType.APPLICATION_JSON);

        OAIDescriptor descriptor = (OAIDescriptor) swaggerService.parse(pageEntity.getContent());

        swaggerService.transform(descriptor,
                Collections.singleton(new PageConfigurationOAITransformer(pageEntity)));

        assertNotNull(descriptor.toJson());
        validateV3(Json.mapper().readTree(descriptor.toJson()));
    }

    @Test
    public void shouldTransformAPIFromSwaggerV3_yaml() throws IOException {
        PageEntity pageEntity = getPage("io/gravitee/management/service/openapi.yaml", MediaType.TEXT_PLAIN);

        OAIDescriptor descriptor = (OAIDescriptor) swaggerService.parse(pageEntity.getContent());

        swaggerService.transform(descriptor,
                Collections.singleton(new PageConfigurationOAITransformer(pageEntity)));

        assertNotNull(descriptor.toYaml());
        validateV3(Yaml.mapper().readTree(descriptor.toYaml()));
    }

    private void validateV2(JsonNode node) {
        assertEquals("1.2.3", node.get("info").get("version").asText());
        assertEquals("Gravitee.io Swagger API", node.get("info").get("title").asText());
        assertEquals("https", node.get("schemes").get(0).asText());
        assertEquals("my.domain.com", node.get("host").asText());
        assertEquals("/v1", node.get("basePath").asText());
    }

    private void validateV3(JsonNode node) {
        assertEquals("1.2.3", node.get("info").get("version").asText());
        assertEquals("Gravitee.io Swagger API", node.get("info").get("title").asText());
        assertEquals("https://my.domain.com/v1", node.get("servers").get(0).get("url").asText());
        assertEquals("oauth2", node.get("components").get("securitySchemes").get("oauth2Scheme").get("type").asText());
    }
}
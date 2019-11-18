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

import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.ApiKeyRepository;
import io.gravitee.repository.management.model.ApiKey;
import io.gravitee.rest.api.model.*;
import io.gravitee.rest.api.service.ApiKeyGenerator;
import io.gravitee.rest.api.service.ApiKeyService;
import io.gravitee.rest.api.service.ApiService;
import io.gravitee.rest.api.service.ApplicationService;
import io.gravitee.rest.api.service.AuditService;
import io.gravitee.rest.api.service.EmailService;
import io.gravitee.rest.api.service.NotifierService;
import io.gravitee.rest.api.service.PlanService;
import io.gravitee.rest.api.service.SubscriptionService;
import io.gravitee.rest.api.service.exceptions.ApiKeyNotFoundException;
import io.gravitee.rest.api.service.exceptions.TechnicalManagementException;
import io.gravitee.rest.api.service.impl.ApiKeyServiceImpl;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.*;

/**
 * @author Azize Elamrani (azize dot elamrani at gmail dot com)
 */
@RunWith(MockitoJUnitRunner.class)
public class ApiKeyServiceTest {

    private static final String API_ID = "myAPI";
    private static final String APPLICATION_ID = "myApplication";
    private static final String PLAN_ID = "myPlan";
    private static final String API_KEY = "ef02ecd0-71bb-11e5-9d70-feff819cdc9f";
    private static final String SUBSCRIPTION_ID = "subscription-1";

    @InjectMocks
    private ApiKeyService apiKeyService = new ApiKeyServiceImpl();

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private PlanService planService;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private EmailService emailService;

    @Mock
    private ApiService apiService;

    @Mock
    private ApiKeyGenerator apiKeyGenerator;

    @Mock
    private ApplicationEntity application;

    @Mock
    private ApiKey apiKey;

    @Mock
    private SubscriptionEntity subscription;

    @Mock
    private PlanEntity plan;

    @Mock
    private AuditService auditService;

    @Mock
    private NotifierService notifierService;

    @Test
    public void shouldGenerate() throws TechnicalException {
        // Generated API Key
        when(apiKeyGenerator.generate()).thenReturn(API_KEY);

        // Prepare subscription
        when(subscription.getId()).thenReturn(SUBSCRIPTION_ID);
        when(subscription.getEndingAt()).thenReturn(Date.from(new Date().toInstant().plus(1, ChronoUnit.DAYS)));
        when(subscriptionService.findById(SUBSCRIPTION_ID)).thenReturn(subscription);

        PlanEntity planEntity = mock(PlanEntity.class);
        when(planEntity.getApi()).thenReturn("apiId");
        when(planService.findById(any())).thenReturn(planEntity);

        // Stub API Key creation
        when(apiKeyRepository.create(any())).thenAnswer(returnsFirstArg());

        // Run
        final ApiKeyEntity apiKey = apiKeyService.generate(SUBSCRIPTION_ID);

        // Verify
        verify(apiKeyRepository, times(1)).create(any());
        assertEquals(API_KEY, apiKey.getKey());
        assertFalse(apiKey.isRevoked());
        assertEquals(subscription.getEndingAt(), apiKey.getExpireAt());
        assertEquals(subscription.getId(), apiKey.getSubscription());
    }

    @Test(expected = TechnicalManagementException.class)
    public void shouldNotGenerateBecauseTechnicalException() {
        when(subscriptionService.findById(SUBSCRIPTION_ID)).thenThrow(TechnicalManagementException.class);

        apiKeyService.generate(SUBSCRIPTION_ID);
    }

    @Test
    public void shouldRevoke() throws Exception {
        apiKey = new ApiKey();
        apiKey.setKey("123-456-789");
        apiKey.setSubscription(SUBSCRIPTION_ID);
        apiKey.setCreatedAt(new Date());
        apiKey.setPlan(PLAN_ID);
        apiKey.setApplication(APPLICATION_ID);
        final ApiModelEntity api = mock(ApiModelEntity.class);
        when(api.getId()).thenReturn("123");

        // Prepare data
        when(subscription.getApplication()).thenReturn(APPLICATION_ID);
        when(subscription.getPlan()).thenReturn(PLAN_ID);
        when(plan.getApi()).thenReturn(API_ID);

        // Stub
        when(apiKeyRepository.findById(API_KEY)).thenReturn(Optional.of(apiKey));
        when(applicationService.findById(subscription.getApplication())).thenReturn(application);
        when(planService.findById(subscription.getPlan())).thenReturn(plan);
        when(apiService.findByIdForTemplates(any())).thenReturn(api);

        // Run
        apiKeyService.revoke(API_KEY, true);

        // Verify
        verify(apiKeyRepository, times(1)).update(any());
    }

    @Test
    public void shouldNotRevokeBecauseAlreadyRevoked() throws Exception {
        // Verify
        verify(apiKeyRepository, never()).update(any());
        verify(emailService, never()).sendEmailNotification(any());
    }

    @Test(expected = ApiKeyNotFoundException.class)
    public void shouldNotRevokeBecauseNotFound() throws TechnicalException {
        when(apiKeyRepository.findById(API_KEY)).thenReturn(Optional.empty());

        apiKeyService.revoke(API_KEY, true);
    }

    @Test(expected = TechnicalManagementException.class)
    public void shouldNotRevokeBecauseTechnicalException() throws TechnicalException {
        when(apiKeyRepository.findById(API_KEY)).thenThrow(TechnicalException.class);

        apiKeyService.revoke(API_KEY, true);
    }

    @Test
    public void shouldRenew() throws TechnicalException {
        // Prepare data
        // apiKey object is not a mock since its state is updated by the call to apiKeyService.renew()
        apiKey = new ApiKey();
        apiKey.setKey("123-456-789");
        apiKey.setSubscription(SUBSCRIPTION_ID);
        apiKey.setCreatedAt(new Date());
        apiKey.setPlan(PLAN_ID);
        apiKey.setApplication(APPLICATION_ID);
        final ApiModelEntity api = mock(ApiModelEntity.class);
        when(api.getId()).thenReturn("123");

        when(subscription.getId()).thenReturn(SUBSCRIPTION_ID);
        when(subscription.getEndingAt()).thenReturn(Date.from(new Date().toInstant().plus(1, ChronoUnit.DAYS)));
        when(subscription.getApplication()).thenReturn(APPLICATION_ID);
        when(subscription.getPlan()).thenReturn(PLAN_ID);
        when(plan.getApi()).thenReturn(API_ID);

        // Stub
        when(apiKeyGenerator.generate()).thenReturn(API_KEY);
        when(subscriptionService.findById(subscription.getId())).thenReturn(subscription);
        when(apiKeyRepository.create(any())).thenAnswer(returnsFirstArg());
        when(apiKeyRepository.findBySubscription(SUBSCRIPTION_ID)).thenReturn(Collections.singleton(apiKey));
        when(applicationService.findById(subscription.getApplication())).thenReturn(application);
        when(planService.findById(subscription.getPlan())).thenReturn(plan);
        when(apiService.findByIdForTemplates(any())).thenReturn(api);

        // Run
        final ApiKeyEntity apiKeyEntity = apiKeyService.renew(SUBSCRIPTION_ID);

        // Verify
        // A new API Key has been created
        verify(apiKeyRepository, times(1)).create(any());
        assertEquals(API_KEY, apiKeyEntity.getKey());

        // Old API Key has been revoked
        verify(apiKeyRepository, times(1)).update(apiKey);
        assertFalse(apiKey.isRevoked());
        assertNotNull(apiKey.getExpireAt());
    }

    /*
    @Test
    public void shouldGenerateAndInvalidOldKeys() throws TechnicalException {
        when(apiKeyGenerator.generate()).thenReturn(API_KEY);
        when(apiKeyRepository.findByApplicationAndApi(APPLICATION_NAME, API_NAME)).thenReturn(new HashSet<>(asList(apiKey)));
        when(apiKeyRepository.create(eq(APPLICATION_NAME), eq(API_NAME), any(ApiKey.class))).thenReturn(apiKey);
        when(apiKey.getKey()).thenReturn(API_KEY);
        when(apiKey.getCreatedAt()).thenReturn(date);
        when(apiKey.isRevoked()).thenReturn(false);

        when(apiKey.getApplication()).thenReturn(APPLICATION_NAME);
        when(applicationService.findById(APPLICATION_NAME)).thenReturn(application);
        when(application.getPrimaryOwner()).thenReturn(primaryOwner);

        final ApiKeyEntity apiKeyEntity = apiKeyService.generateOrRenew(APPLICATION_NAME, API_NAME);

        verify(apiKey).setExpiration(any());
        verify(apiKeyRepository).update(apiKey);

        assertEquals(API_KEY, apiKeyEntity.getKey());
        assertEquals(date, apiKeyEntity.getCreatedAt());
        assertEquals(false, apiKeyEntity.isRevoked());
    }

    @Test(expected = TechnicalManagementException.class)
    public void shouldNotGenerateBecauseTechnicalException() throws TechnicalException {
        when(apiKeyGenerator.generate()).thenReturn(API_KEY);
        when(apiKeyRepository.findByApplicationAndApi(APPLICATION_NAME, API_NAME))
            .thenThrow(TechnicalException.class);

        apiKeyService.generateOrRenew(APPLICATION_NAME, API_NAME);
    }

    @Test
    public void shouldRevoke() throws TechnicalException {
        when(apiKeyRepository.retrieve(API_KEY)).thenReturn(Optional.of(apiKey));
        when(apiKey.isRevoked()).thenReturn(false);

        when(apiKey.getApplication()).thenReturn(APPLICATION_NAME);
        when(applicationService.findById(APPLICATION_NAME)).thenReturn(application);
        when(application.getPrimaryOwner()).thenReturn(primaryOwner);

        apiKeyService.revoke(API_KEY);

        verify(apiKeyRepository).update(apiKey);
    }

    @Test
    public void shouldNotRevokeBecauseAlreadyRevoked() throws TechnicalException {
        when(apiKeyRepository.retrieve(API_KEY)).thenReturn(Optional.of(apiKey));
        when(apiKey.isRevoked()).thenReturn(true);

        apiKeyService.revoke(API_KEY);

        verify(apiKeyRepository, never()).update(apiKey);
    }

    @Test(expected = ApiKeyNotFoundException.class)
    public void shouldNotRevokeBecauseNotFound() throws TechnicalException {
        when(apiKeyRepository.retrieve(API_KEY)).thenReturn(Optional.empty());

        apiKeyService.revoke(API_KEY);
    }

    @Test(expected = TechnicalManagementException.class)
    public void shouldNotRevokeBecauseTechnicalException() throws TechnicalException {
        when(apiKeyRepository.retrieve(API_KEY)).thenThrow(TechnicalException.class);

        apiKeyService.revoke(API_KEY);
    }

    @Test
    public void shouldGetCurrent() throws TechnicalException {
        final ApiKey maxApiKey = mock(ApiKey.class);

        final Date value = new Date();
        when(apiKey.getCreatedAt()).thenReturn(value);
        when(apiKey.getKey()).thenReturn(API_KEY);
        when(maxApiKey.getCreatedAt()).thenReturn(Date.from(value.toInstant().plus(1, ChronoUnit.DAYS)));
        when(maxApiKey.getKey()).thenReturn("KEY");

        when(apiKeyRepository.findByApplicationAndApi(APPLICATION_NAME, API_KEY)).thenReturn(new HashSet(asList(apiKey, maxApiKey)));

        final Optional<ApiKeyEntity> currentApiKey = apiKeyService.getCurrent(APPLICATION_NAME, API_KEY);

        assertNotNull(currentApiKey);
        assertTrue(currentApiKey.isPresent());
        assertEquals("KEY", currentApiKey.get().getKey());
    }

    @Test
    public void shouldNotGetCurrentBecauseAllRevoked() throws TechnicalException {
        final ApiKey maxApiKey = mock(ApiKey.class);

        final Date value = new Date();
        when(apiKey.getCreatedAt()).thenReturn(value);
        when(apiKey.getKey()).thenReturn(API_KEY);
        when(apiKey.isRevoked()).thenReturn(true);
        when(maxApiKey.getCreatedAt()).thenReturn(Date.from(value.toInstant().plus(1, ChronoUnit.DAYS)));
        when(maxApiKey.getKey()).thenReturn("KEY");
        when(maxApiKey.isRevoked()).thenReturn(true);

        when(apiKeyRepository.findByApplicationAndApi(APPLICATION_NAME, API_KEY)).thenReturn(new HashSet(asList(apiKey, maxApiKey)));

        final Optional<ApiKeyEntity> currentApiKey = apiKeyService.getCurrent(APPLICATION_NAME, API_KEY);

        assertNotNull(currentApiKey);
        assertFalse(currentApiKey.isPresent());
    }

    @Test
    public void shouldNotGetCurrentBecauseNotFound() throws TechnicalException {
        when(apiKeyRepository.findByApplicationAndApi(APPLICATION_NAME, API_KEY)).thenReturn(null);

        final Optional<ApiKeyEntity> currentApiKey = apiKeyService.getCurrent(APPLICATION_NAME, API_KEY);

        assertNotNull(currentApiKey);
        assertFalse(currentApiKey.isPresent());
    }

    @Test(expected = TechnicalManagementException.class)
    public void shouldNotGetCurrentBecauseTechnicalException() throws TechnicalException {
        when(apiKeyRepository.findByApplicationAndApi(APPLICATION_NAME, API_KEY)).thenThrow(TechnicalException.class);

        apiKeyService.getCurrent(APPLICATION_NAME, API_KEY);
    }

    @Test
    public void shouldFindAll() throws TechnicalException {
        final ApiKey maxApiKey = mock(ApiKey.class);

        final Date value = new Date();
        when(apiKey.getCreatedAt()).thenReturn(value);
        when(apiKey.getKey()).thenReturn(API_KEY);
        when(maxApiKey.getCreatedAt()).thenReturn(Date.from(value.toInstant().plus(1, ChronoUnit.DAYS)));
        when(maxApiKey.getKey()).thenReturn("KEY");

        when(apiKeyRepository.findByApplicationAndApi(APPLICATION_NAME, API_KEY)).thenReturn(new HashSet(asList(apiKey, maxApiKey)));

        final Set<ApiKeyEntity> apiKeyEntities = apiKeyService.findAll(APPLICATION_NAME, API_KEY);

        assertNotNull(apiKeyEntities);
        assertEquals(2, apiKeyEntities.size());
    }

    @Test
    public void shouldNotFindAllBecauseNotFound() throws TechnicalException {
        when(apiKeyRepository.findByApplicationAndApi(APPLICATION_NAME, API_KEY)).thenReturn(null);

        final Set<ApiKeyEntity> apiKeyEntities = apiKeyService.findAll(APPLICATION_NAME, API_KEY);

        assertNotNull(apiKeyEntities);
        assertTrue(apiKeyEntities.isEmpty());
    }

    @Test(expected = TechnicalManagementException.class)
    public void shouldNotFindAllBecauseTechnicalException() throws TechnicalException {
        when(apiKeyRepository.findByApplicationAndApi(APPLICATION_NAME, API_KEY)).thenThrow(TechnicalException.class);

        apiKeyService.findAll(APPLICATION_NAME, API_KEY);
    }
    */
}

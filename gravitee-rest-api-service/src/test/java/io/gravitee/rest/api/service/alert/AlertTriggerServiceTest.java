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
package io.gravitee.rest.api.service.alert;

import io.gravitee.alert.api.trigger.Trigger;
import io.gravitee.plugin.alert.AlertEngineService;
import io.gravitee.rest.api.model.PrimaryOwnerEntity;
import io.gravitee.rest.api.model.alert.AlertEntity;
import io.gravitee.rest.api.model.api.ApiEntity;
import io.gravitee.rest.api.service.ApiService;
import io.gravitee.rest.api.service.alert.AlertTriggerService;
import io.gravitee.rest.api.service.alert.impl.AlertTriggerServiceImpl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import java.util.concurrent.CompletableFuture;

import static io.gravitee.rest.api.model.alert.AlertReferenceType.API;
import static io.gravitee.rest.api.model.alert.AlertType.HEALTH_CHECK;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AlertTriggerServiceTest {

    @InjectMocks
    private AlertTriggerService alertTriggerService = new AlertTriggerServiceImpl();

    @Mock
    private AlertEngineService alertEngineService;
    @Mock
    private ApiService apiService;
    @Spy
    private ConfigurableEnvironment environment = new StandardEnvironment();

    @Mock
    private ApiEntity api;
    @Mock
    private AlertEntity alert;
    @Mock
    private PrimaryOwnerEntity primaryOwner;

    @Before
    public void init() {
        setField(alertTriggerService, "alertEnabled", true);
        setField(alertTriggerService, "subject", "[Gravitee.io] %s");
        when(api.getPrimaryOwner()).thenReturn(primaryOwner);
        when(primaryOwner.getEmail()).thenReturn("test@email.com");
        when(alert.getId()).thenReturn("alert-id");
        when(alert.getType()).thenReturn(HEALTH_CHECK);
        when(alert.getReferenceType()).thenReturn(API);
        when(alert.getReferenceId()).thenReturn("123");
        when(apiService.findById("123")).thenReturn(api);
        when(alertEngineService.send(any(Trigger.class))).thenReturn(new CompletableFuture<>());
    }

    @Test
    public void shouldTriggerAlert() {
        alertTriggerService.trigger(alert);

        verify(alertEngineService).send(argThat((ArgumentMatcher<Trigger>) trigger -> ".type == \"HEALTH_CHECK\" and .props.API == \"123\"".equals(trigger.getCondition())));
    }

    @Test
    public void shouldTriggerAlertOnce() {
        alertTriggerService.trigger(alert);
        alertTriggerService.trigger(alert);
        verify(alertEngineService, times(2)).send(any(Trigger.class));
    }

    @Test
    public void shouldNotTriggerAlertBecauseNoEmailConfigured() {
        when(primaryOwner.getEmail()).thenReturn(null);
        alertTriggerService.trigger(alert);
        verify(alertEngineService, times(0)).send(any(Trigger.class));
    }

    @Test
    public void shouldDisableAlert() {
        alertTriggerService.trigger(alert);
        alertTriggerService.disable(alert);

        verify(alertEngineService).send(argThat((ArgumentMatcher<Trigger>) trigger -> alert.getId().equals(trigger.getId()) && trigger.getEnabled() != null && !trigger.getEnabled()));
    }

    @Test
    public void shouldNotTriggerOrDisableAlertBecauseDisabled() {
        setField(alertTriggerService, "alertEnabled", false);
        alertTriggerService.trigger(alert);
        alertTriggerService.disable(alert);
        verify(alertEngineService, times(0)).send(any(Trigger.class));
    }
}

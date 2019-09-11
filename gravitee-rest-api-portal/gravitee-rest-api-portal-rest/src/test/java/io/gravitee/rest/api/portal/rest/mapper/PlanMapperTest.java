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
package io.gravitee.rest.api.portal.rest.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import io.gravitee.common.http.HttpMethod;
import io.gravitee.definition.model.Path;
import io.gravitee.definition.model.Policy;
import io.gravitee.definition.model.Rule;
import io.gravitee.rest.api.model.PlanEntity;
import io.gravitee.rest.api.model.PlanSecurityType;
import io.gravitee.rest.api.model.PlanStatus;
import io.gravitee.rest.api.model.PlanType;
import io.gravitee.rest.api.model.PlanValidationType;
import io.gravitee.rest.api.model.SubscriptionEntity;
import io.gravitee.rest.api.portal.rest.model.Plan;
import io.gravitee.rest.api.portal.rest.model.Plan.SecurityEnum;
import io.gravitee.rest.api.portal.rest.model.Plan.ValidationEnum;
import io.gravitee.rest.api.service.ApplicationService;
import io.gravitee.rest.api.service.SubscriptionService;

/**
 * @author Florent CHAMFROY (florent.chamfroy at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class PlanMapperTest {

    private static final String PLAN_API = "my-plan-api";
    private static final String PLAN_ID = "my-plan-id";
    private static final String PLAN_CHARACTERISTIC = "my-plan-characteristic";
    private static final String PLAN_COMMENT_MESSAGE = "my-plan-comment-message";
    private static final String PLAN_DESCRIPTION = "my-plan-description";
    private static final String PLAN_GROUP = "my-plan-group";
    private static final String PLAN_NAME = "my-plan-name";
    private static final String PLAN_RULE_POLICY_CONFIGURATION = "my-plan-rule-policy-configuration";
    private static final String PLAN_RULE_POLICY_NAME = "my-plan-rule-policy-name";
    private static final String PLAN_RULE_DESCRIPTION = "my-plan-rule-description";
    private static final String PLAN_PATH = "my-plan-path";
    private static final String PLAN_SECURITY_DEFINITINON = "my-plan-security-definition";
    private static final String PLAN_SELECTION_RULE = "my-plan-selection-rule";
    private static final String PLAN_TAG = "my-plan-tag";

    private PlanEntity planEntity;

    @InjectMocks
    private PlanMapper planMapper;
    
    @Mock
    private SubscriptionService subscriptionService;
    
    @Mock
    private ApplicationService applicationService;
    
    @Before
    public void init() {
        Instant now = Instant.now();
        Date nowDate = Date.from(now);

        //init
        planEntity = new PlanEntity();
       
        planEntity.setApi(PLAN_API);
        planEntity.setCharacteristics(Arrays.asList(PLAN_CHARACTERISTIC));
        planEntity.setClosedAt(nowDate);
        planEntity.setCommentMessage(PLAN_COMMENT_MESSAGE);
        planEntity.setCommentRequired(true);
        planEntity.setCreatedAt(nowDate);
        planEntity.setDescription(PLAN_DESCRIPTION);
        planEntity.setExcludedGroups(Arrays.asList(PLAN_GROUP));
        planEntity.setId(PLAN_ID);
        planEntity.setName(PLAN_NAME);
        planEntity.setNeedRedeployAt(nowDate);
        planEntity.setOrder(1);
        
        Policy policy = new Policy();
        policy.setConfiguration(PLAN_RULE_POLICY_CONFIGURATION);
        policy.setName(PLAN_RULE_POLICY_NAME);
        Rule rule = new Rule();
        rule.setDescription(PLAN_RULE_DESCRIPTION);
        rule.setEnabled(true);
        rule.setMethods(new HashSet<HttpMethod>(Arrays.asList(HttpMethod.GET)));
        rule.setPolicy(policy);
        Path path = new Path();
        path.setPath(PLAN_PATH);
        path.setRules(Arrays.asList(rule));
        Map<String, Path> paths = new HashMap<>();
        paths.put(PLAN_ID, path);
        planEntity.setPaths(paths);
        
        planEntity.setPublishedAt(nowDate);
        planEntity.setSecurity(PlanSecurityType.API_KEY);
        planEntity.setSecurityDefinition(PLAN_SECURITY_DEFINITINON);
        planEntity.setSelectionRule(PLAN_SELECTION_RULE);
        planEntity.setStatus(PlanStatus.PUBLISHED);
        planEntity.setTags(new HashSet<String>(Arrays.asList(PLAN_TAG)));
        planEntity.setType(PlanType.API);
        planEntity.setUpdatedAt(nowDate);
        planEntity.setValidation(PlanValidationType.AUTO);
        
        
    }
    
    @Test
    public void testConvertWithSubscriptions() {
        doReturn(Arrays.asList(new SubscriptionEntity())).when(subscriptionService).search(any());

        Plan responsePlan = planMapper.convert(planEntity, "user");
        assertNotNull(responsePlan);
        
        List<String> characteristics = responsePlan.getCharacteristics();
        assertNotNull(characteristics);
        assertEquals(1, characteristics.size());
        assertEquals(PLAN_CHARACTERISTIC, characteristics.get(0));
        assertEquals(PLAN_COMMENT_MESSAGE, responsePlan.getCommentQuestion());
        assertTrue(responsePlan.getCommentRequired());
        assertEquals(PLAN_DESCRIPTION, responsePlan.getDescription());
        assertEquals(PLAN_ID, responsePlan.getId());
        assertEquals(PLAN_NAME, responsePlan.getName());
        assertEquals(1, responsePlan.getOrder().intValue());
        assertEquals(SecurityEnum.API_KEY, responsePlan.getSecurity());
        assertTrue(responsePlan.getSubscribed());
        assertEquals(ValidationEnum.AUTO, responsePlan.getValidation());
        
    }
    
    @Test
    public void testConvertWithoutSubscription() {
        //Empty subscription lList
        doReturn(new ArrayList<>()).when(subscriptionService).search(any());
        Plan responsePlan = planMapper.convert(planEntity, "user");
        assertFalse(responsePlan.getSubscribed());
        
        //Null subscription List
        doReturn(null).when(subscriptionService).search(any());
        responsePlan = planMapper.convert(planEntity, "user");
        assertFalse(responsePlan.getSubscribed());
        
    }
}

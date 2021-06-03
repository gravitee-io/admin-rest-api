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
package io.gravitee.rest.api.service.cockpit.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.cockpit.api.command.CommandStatus;
import io.gravitee.cockpit.api.command.bridge.BridgeCommand;
import io.gravitee.cockpit.api.command.bridge.BridgeMultiReply;
import io.gravitee.rest.api.model.EnvironmentEntity;
import io.gravitee.rest.api.model.promotion.PromotionTargetEntity;
import io.gravitee.rest.api.service.cockpit.command.CockpitCommandService;
import io.gravitee.rest.api.service.cockpit.command.bridge.BridgeCommandFactory;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CockpitServiceImpl implements CockpitService {

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(CockpitServiceImpl.class);

    private BridgeCommandFactory bridgeCommandFactory;
    private CockpitCommandService cockpitCommandService;
    private ObjectMapper objectMapper;

    public CockpitServiceImpl(
        BridgeCommandFactory bridgeCommandFactory,
        CockpitCommandService cockpitCommandService,
        ObjectMapper objectMapper
    ) {
        this.bridgeCommandFactory = bridgeCommandFactory;
        this.cockpitCommandService = cockpitCommandService;
        this.objectMapper = objectMapper;
    }

    @Override
    public CockpitReply<List<PromotionTargetEntity>> listPromotionTargets(String organizationId) {
        final BridgeCommand listEnvironmentCommand = this.bridgeCommandFactory.createListEnvironmentCommand();
        final BridgeMultiReply reply = (BridgeMultiReply) cockpitCommandService.send(listEnvironmentCommand);

        if (CommandStatus.SUCCEEDED == reply.getCommandStatus()) {
            final List<PromotionTargetEntity> environmentEntities = reply
                .getReplies()
                .stream()
                .filter(simpleReply -> CommandStatus.SUCCEEDED == simpleReply.getCommandStatus())
                .map(
                    simpleReply -> {
                        try {
                            final EnvironmentEntity environmentEntity =
                                this.objectMapper.readValue(simpleReply.getPayload(), EnvironmentEntity.class);
                            return new PromotionTargetEntity(environmentEntity, simpleReply.getInstallationId());
                        } catch (JsonProcessingException e) {
                            logger.warn(
                                "Problem while deserializing environment {} with payload {}",
                                simpleReply.getEnvironmentId(),
                                simpleReply.getPayload()
                            );
                            return null;
                        }
                    }
                )
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            return new CockpitReply(environmentEntities, CockpitReplyStatus.SUCCEEDED);
        }
        logger.warn("Problem while listing promotion targets through cockpit. \n {}", reply.getMessage());
        return new CockpitReply(Collections.emptyList(), CockpitReplyStatus.ERROR);
    }
}
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
package io.gravitee.rest.api.service.impl.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.model.MessageRecipient;
import io.gravitee.rest.api.model.ApiPageEntity;
import io.gravitee.rest.api.model.PageEntity;
import io.gravitee.rest.api.model.UserEntity;
import io.gravitee.rest.api.model.api.ApiEntity;
import io.gravitee.rest.api.model.command.CommandSearchIndexerEntity;
import io.gravitee.rest.api.model.command.CommandTags;
import io.gravitee.rest.api.model.command.NewCommandEntity;
import io.gravitee.rest.api.model.search.Indexable;
import io.gravitee.rest.api.service.ApiService;
import io.gravitee.rest.api.service.CommandService;
import io.gravitee.rest.api.service.PageService;
import io.gravitee.rest.api.service.UserService;
import io.gravitee.rest.api.service.exceptions.TechnicalManagementException;
import io.gravitee.rest.api.service.impl.search.lucene.DocumentSearcher;
import io.gravitee.rest.api.service.impl.search.lucene.DocumentTransformer;
import io.gravitee.rest.api.service.impl.search.lucene.SearchEngineIndexer;
import io.gravitee.rest.api.service.search.SearchEngineService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class SearchEngineServiceImpl implements SearchEngineService {

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(SearchEngineServiceImpl.class);

    @Autowired
    private SearchEngineIndexer indexer;

    @Autowired
    private Collection<DocumentTransformer> transformers;

    @Autowired
    private Collection<DocumentSearcher> searchers;

    @Autowired
    @Lazy
    private CommandService commandService;

    @Autowired
    @Lazy
    private ApiService apiService;

    @Autowired
    @Lazy
    private PageService pageService;

    @Autowired
    @Lazy
    private UserService userService;

    private ObjectMapper mapper = new ObjectMapper();

    private static final String ACTION_INDEX = "I";
    private static final String ACTION_DELETE = "D";

    @Async
    @Override
    public void index(Indexable source, boolean locally) {
        if (locally) {
            indexLocally(source);
        } else {
            CommandSearchIndexerEntity content = new CommandSearchIndexerEntity();
            content.setAction(ACTION_INDEX);
            content.setId(source.getId());
            content.setClazz(source.getClass().getName());

            sendCommands(content);
        }
    }

    @Async
    @Override
    public void delete(Indexable source, boolean locally) {
        if (locally) {
            deleteLocally(source);
        } else {
            CommandSearchIndexerEntity content = new CommandSearchIndexerEntity();
            content.setAction(ACTION_DELETE);
            content.setId(source.getId());
            content.setClazz(source.getClass().getName());

            sendCommands(content);
        }
    }

    private void sendCommands(CommandSearchIndexerEntity content) {
        try {
            NewCommandEntity msg = new NewCommandEntity();
            msg.setTags(Collections.singletonList(CommandTags.DATA_TO_INDEX));
            msg.setTo(MessageRecipient.MANAGEMENT_APIS.name());
            msg.setTtlInSeconds(60);
            msg.setContent(mapper.writeValueAsString(content));
            commandService.send(msg);
        } catch (JsonProcessingException e) {
            logger.error("Unexpected error while sending a message", e);
        }
    }

    @Override
    public void process(CommandSearchIndexerEntity content) {
        Indexable source = getSource(content.getClazz(), content.getId());
        if (source == null) {
            logger.error("Unable to get source from message content [{}]", content);
            throw new TechnicalManagementException("Unable to get source from message content [" + content + "]");
        }

        if (ACTION_DELETE.equals(content.getAction())) {
            deleteLocally(source);
        } else if (ACTION_INDEX.equals(content.getAction())) {
            indexLocally(source);
        }
    }

    private Indexable getSource(String clazz, String id) {
        if (ApiEntity.class.getName().equals(clazz)) {
            return apiService.findById(id);
        } else if (PageEntity.class.getName().equals(clazz) || ApiPageEntity.class.getName().equals(clazz)) {
            return pageService.findById(id);
        } else if (UserEntity.class.getName().equals(clazz)) {
            return userService.findById(id);
        }
        return null;
    }

    private void indexLocally(Indexable source) {
        transformers.stream()
                .filter(transformer -> transformer.handle(source.getClass()))
                .findFirst()
                .ifPresent(transformer -> {
                    try {
                        indexer.index(transformer.transform(source));
                    } catch (TechnicalException te) {
                        logger.error("Unexpected error while indexing a document", te);
                    }
                });
    }

    private void deleteLocally(Indexable source) {
        transformers.stream()
                .filter(transformer -> transformer.handle(source.getClass()))
                .findFirst()
                .ifPresent(transformer -> {
                    try {
                        indexer.remove(transformer.transform(source));
                    } catch (TechnicalException te) {
                        logger.error("Unexpected error while deleting a document", te);
                    }
                });
    }

    @Override
    public SearchResult search(io.gravitee.rest.api.service.search.query.Query<? extends Indexable> query) {
        Optional<SearchResult> results = searchers.stream()
                .filter(searcher -> searcher.handle(query.getRoot()))
                .findFirst()
                .flatMap(searcher -> {
                    try {
                        return Optional.of(searcher.search(query));
                    } catch (TechnicalException te) {
                        logger.error("Unexpected error while deleting a document", te);
                        return Optional.empty();
                    }
                });

        return results.get();
    }

}

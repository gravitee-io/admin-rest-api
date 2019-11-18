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
package io.gravitee.rest.api.service.impl.search.lucene.transformer;

import io.gravitee.rest.api.model.api.ApiEntity;
import io.gravitee.rest.api.model.search.Indexable;
import io.gravitee.rest.api.service.impl.search.lucene.DocumentTransformer;

import org.apache.lucene.document.*;
import org.springframework.stereotype.Component;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ApiDocumentTransformer implements DocumentTransformer {

    private final static String FIELD_ID = "id";
    private final static String FIELD_TYPE = "type";
    private final static String FIELD_TYPE_VALUE = "api";
    private final static String FIELD_NAME = "name";
    private final static String FIELD_NAME_LOWERCASE = "name_lowercase";
    private final static String FIELD_NAME_SPLIT = "name_split";
    private final static String FIELD_DESCRIPTION = "description";
    private final static String FIELD_OWNER = "ownerName";
    private final static String FIELD_OWNER_MAIL = "ownerMail";
    private final static String FIELD_LABELS = "labels";
    private final static String FIELD_VIEWS = "views";
    private final static String FIELD_CREATED_AT = "createdAt";
    private final static String FIELD_UPDATED_AT = "updatedAt";
    private final static String FIELD_PATH = "path";
    private final static String FIELD_PATH_SPLIT = "path_split";
    private final static String FIELD_TAGS = "tags";

    @Override
    public Document transform(Indexable indexable) {
        Document doc = new Document();
        ApiEntity api = (ApiEntity) indexable;

        doc.add(new StringField(FIELD_ID, api.getId(), Field.Store.YES));
        doc.add(new StringField(FIELD_TYPE, FIELD_TYPE_VALUE, Field.Store.YES));
        doc.add(new StringField(FIELD_NAME, api.getName(), Field.Store.NO));
        doc.add(new StringField(FIELD_NAME_LOWERCASE, api.getName().toLowerCase(), Field.Store.NO));
        doc.add(new TextField(FIELD_NAME_SPLIT, api.getName(), Field.Store.NO));
        doc.add(new TextField(FIELD_DESCRIPTION, api.getDescription(), Field.Store.NO));
        doc.add(new TextField(FIELD_OWNER, api.getPrimaryOwner().getDisplayName(), Field.Store.NO));
        if (api.getPrimaryOwner().getEmail() != null) {
            doc.add(new TextField(FIELD_OWNER_MAIL, api.getPrimaryOwner().getEmail(), Field.Store.NO));
        }
        doc.add(new StringField(FIELD_PATH, api.getProxy().getContextPath(), Field.Store.NO));
        doc.add(new TextField(FIELD_PATH_SPLIT, api.getProxy().getContextPath(), Field.Store.NO));

        // labels
        if (api.getLabels() != null) {
            for (String label : api.getLabels()) {
                doc.add(new TextField(FIELD_LABELS, label, Field.Store.NO));
            }
        }

        // views
        if (api.getViews() != null) {
            for (String view : api.getViews()) {
                doc.add(new TextField(FIELD_VIEWS, view, Field.Store.NO));
            }
        }

        // tags
        if (api.getTags() != null) {
            for (String tag : api.getTags()) {
                doc.add(new TextField(FIELD_TAGS, tag, Field.Store.NO));
            }
        }

        doc.add(new LongPoint(FIELD_CREATED_AT, api.getCreatedAt().getTime()));
        doc.add(new LongPoint(FIELD_UPDATED_AT, api.getUpdatedAt().getTime()));

        return doc;
    }

    @Override
    public boolean handle(Class<? extends Indexable> source) {
        return ApiEntity.class.isAssignableFrom(source);
    }
}

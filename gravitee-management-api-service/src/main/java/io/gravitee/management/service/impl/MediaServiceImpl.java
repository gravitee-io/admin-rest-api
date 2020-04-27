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
package io.gravitee.management.service.impl;

import io.gravitee.common.utils.UUID;
import io.gravitee.management.model.MediaEntity;
import io.gravitee.management.service.ConfigService;
import io.gravitee.management.service.MediaService;
import io.gravitee.management.service.exceptions.TechnicalManagementException;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.media.api.MediaRepository;
import io.gravitee.repository.media.model.Media;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * @author Guillaume Gillon
 */
@Component
public class MediaServiceImpl implements MediaService {

    private static final Logger logger = LoggerFactory.getLogger(MediaServiceImpl.class);

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private ConfigService configService;

    @Override
    public String savePortalMedia(MediaEntity mediaEntity) {
        return this.saveApiMedia(null, mediaEntity);
    }

    @Override
    public String saveApiMedia(String api, MediaEntity mediaEntity) {

        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(mediaEntity.getData());
            String hashString = DatatypeConverter.printHexBinary(hash);

            String id = UUID.toString(UUID.random());

            Optional<Media> checkMedia = null;

            if (api != null) {
                checkMedia = mediaRepository.findByHash(hashString, api, mediaEntity.getType());
            } else {
                checkMedia = mediaRepository.findByHash(hashString, mediaEntity.getType());
            }


            if(checkMedia.isPresent()) {
                return checkMedia.get().getHash();
            } else {
                Media media = convert(mediaEntity);
                media.setId(id);
                media.setHash(hashString);
                media.setSize((long) mediaEntity.getData().length);
                media.setApi(api);
                media.setData(mediaEntity.getData());
                mediaRepository.save(media);

                return hashString;
            }

        } catch (TechnicalException | NoSuchAlgorithmException ex) {
            logger.error("An error occurs while trying to create {}", mediaEntity, ex);
            throw new TechnicalManagementException("An error occurs while trying create " + mediaEntity, ex);
        }
    }

    @Override
    public MediaEntity findby(String id) {
        Optional<Media> mediaData = mediaRepository.findByHash(id, "image");
        return mediaData.isPresent() ? convert(mediaData.get()) : null;
    }

    @Override
    public MediaEntity findby(String id, String api) {
        Optional<Media> mediaData = mediaRepository.findByHash(id, api, "image");
        return mediaData.isPresent() ? convert(mediaData.get()): null;
    }

    public Long getMediaMaxSize() {
        return Long.valueOf(configService.getPortalConfig().getPortal().getUploadMedia().getMaxSizeInOctet());
    }

    private static Media convert(MediaEntity imageEntity) {
        Media media = new Media();
        media.setFileName(imageEntity.getFileName());
        media.setSize(imageEntity.getSize());
        media.setType(imageEntity.getType());
        media.setSubType(imageEntity.getSubType());
        //media.setData(new ByteArrayInputStream(imageEntity.getData()));
        return media;
    }

    private static MediaEntity convert(Media media) {
        MediaEntity mediaEntity = new MediaEntity(
                media.getData(),
                media.getType(),
                media.getSubType(),
                media.getFileName(),
                media.getSize());
        mediaEntity.setUploadDate(media.getCreatedAt());
        return mediaEntity;
    }
}

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
package io.gravitee.rest.api.model;

import io.swagger.annotations.ApiModelProperty;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PrimaryOwnerEntity {
    @ApiModelProperty(
            value = "The user or group id.",
            example = "005197cc-cc84-86a6-a75a-88f9772c67db")
    private String id;

    @ApiModelProperty(
            value = "The user or group email.",
            example = "contact@gravitee.io")
    private String email;

    @ApiModelProperty(
            value = "The user or group display name.",
            example = "John Doe")
    private String displayName;

    @ApiModelProperty(
        value = "The primary owner type",
        example = "USER")
    private PrimaryOwnerType type;

    public PrimaryOwnerEntity(){

    }

    public PrimaryOwnerEntity(PrimaryOwner primaryOwner) {
        this.id = primaryOwner.getId();
        this.email = primaryOwner.getEmail();
        this.displayName = primaryOwner.getDisplayName();
        this.type = primaryOwner.getType();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public PrimaryOwnerType getType() {
        return type;
    }

    public void setType(PrimaryOwnerType type) {
        this.type = type;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setId(String id) {
        this.id = id;
    }
}

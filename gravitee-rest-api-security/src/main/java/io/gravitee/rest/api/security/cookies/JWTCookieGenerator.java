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
package io.gravitee.rest.api.security.cookies;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import static io.gravitee.rest.api.service.common.JWTHelper.DefaultValues.DEFAULT_JWT_EXPIRE_AFTER;

import javax.servlet.http.Cookie;

/**
 * @author Azize Elamrani (azize at gravitee.io)
 * @author GraviteeSource Team
 */
public class JWTCookieGenerator {

    private static final boolean DEFAULT_JWT_COOKIE_SECURE = false;
    private static final String DEFAULT_JWT_COOKIE_PATH = "/";
    private static final String DEFAULT_JWT_COOKIE_DOMAIN = "";

    @Autowired
    private Environment environment;

    public Cookie generate(final String value) {
        final Cookie cookie = new Cookie("Auth-Graviteeio-APIM", value);
        cookie.setHttpOnly(true);
        cookie.setSecure(environment.getProperty("jwt.cookie-secure", Boolean.class, DEFAULT_JWT_COOKIE_SECURE));
        cookie.setPath(environment.getProperty("jwt.cookie-path", DEFAULT_JWT_COOKIE_PATH));
        cookie.setDomain(environment.getProperty("jwt.cookie-domain", DEFAULT_JWT_COOKIE_DOMAIN));
        cookie.setMaxAge(value == null? 0 : environment.getProperty("jwt.expire-after", Integer.class, DEFAULT_JWT_EXPIRE_AFTER));
        return cookie;
    }
}

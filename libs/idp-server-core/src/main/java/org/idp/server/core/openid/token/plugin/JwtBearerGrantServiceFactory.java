/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.openid.token.plugin;

import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.core.openid.token.service.JwtBearerGrantService;
import org.idp.server.core.openid.token.service.OAuthTokenCreationService;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.http.HttpRequestExecutor;

/**
 * Factory for creating JwtBearerGrantService instances.
 *
 * <p>This factory is registered via SPI to enable JWT Bearer Grant support.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523">RFC 7523</a>
 */
public class JwtBearerGrantServiceFactory implements OAuthTokenCreationServiceFactory {

  @Override
  public OAuthTokenCreationService create(ApplicationComponentContainer container) {
    OAuthTokenCommandRepository oAuthTokenCommandRepository =
        container.resolve(OAuthTokenCommandRepository.class);
    HttpRequestExecutor httpRequestExecutor = container.resolve(HttpRequestExecutor.class);
    return new JwtBearerGrantService(oAuthTokenCommandRepository, httpRequestExecutor);
  }
}

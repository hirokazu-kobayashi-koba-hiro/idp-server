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

import java.util.Map;
import org.idp.server.core.openid.identity.UserRegistrator;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.type.oauth.SubjectTokenType;
import org.idp.server.core.openid.plugin.token.SubjectTokenVerificationStrategyPluginLoader;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.core.openid.token.service.ExternalTokenIntrospector;
import org.idp.server.core.openid.token.service.FederationJwtVerifier;
import org.idp.server.core.openid.token.service.OAuthTokenCreationService;
import org.idp.server.core.openid.token.service.TokenExchangeGrantService;
import org.idp.server.core.openid.token.verifier.SubjectTokenVerificationStrategies;
import org.idp.server.core.openid.token.verifier.SubjectTokenVerificationStrategy;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.http.HttpRequestExecutor;

/**
 * Factory for creating TokenExchangeGrantService instances.
 *
 * <p>This factory is registered via SPI to enable Token Exchange Grant support. Core token types
 * (jwt, id_token, access_token) are built into {@link SubjectTokenVerifier}. Extension token types
 * (e.g., SAML) are loaded via {@link SubjectTokenVerificationStrategyPluginLoader} from internal
 * modules and external plugins/ directory.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693</a>
 */
public class TokenExchangeGrantServiceFactory implements OAuthTokenCreationServiceFactory {

  @Override
  public OAuthTokenCreationService create(ApplicationComponentContainer container) {
    OAuthTokenCommandRepository oAuthTokenCommandRepository =
        container.resolve(OAuthTokenCommandRepository.class);
    HttpRequestExecutor httpRequestExecutor = container.resolve(HttpRequestExecutor.class);
    UserQueryRepository userQueryRepository = container.resolve(UserQueryRepository.class);
    UserCommandRepository userCommandRepository = container.resolve(UserCommandRepository.class);
    UserRegistrator userRegistrator =
        new UserRegistrator(userQueryRepository, userCommandRepository);

    FederationJwtVerifier federationJwtVerifier = new FederationJwtVerifier(httpRequestExecutor);
    ExternalTokenIntrospector externalTokenIntrospector =
        new ExternalTokenIntrospector(httpRequestExecutor);

    Map<SubjectTokenType, SubjectTokenVerificationStrategy> extensionStrategies =
        SubjectTokenVerificationStrategyPluginLoader.load(container);

    SubjectTokenVerificationStrategies subjectTokenVerificationStrategies =
        new SubjectTokenVerificationStrategies(
            federationJwtVerifier, externalTokenIntrospector, extensionStrategies);

    return new TokenExchangeGrantService(
        oAuthTokenCommandRepository, userRegistrator, subjectTokenVerificationStrategies);
  }
}

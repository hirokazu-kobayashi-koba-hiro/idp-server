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

package org.idp.server.core.openid.oauth;

import org.idp.server.core.openid.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.openid.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.dependency.protocol.ProtocolProvider;
import org.idp.server.platform.http.HttpRequestExecutor;

public class DefaultOAuthProtocolProvider implements ProtocolProvider<OAuthProtocol> {

  @Override
  public Class<OAuthProtocol> type() {
    return OAuthProtocol.class;
  }

  @Override
  public OAuthProtocol provide(ApplicationComponentContainer container) {
    AuthorizationRequestRepository authorizationRequestRepository =
        container.resolve(AuthorizationRequestRepository.class);
    AuthorizationServerConfigurationQueryRepository
        authorizationServerConfigurationQueryRepository =
            container.resolve(AuthorizationServerConfigurationQueryRepository.class);
    ClientConfigurationQueryRepository clientConfigurationQueryRepository =
        container.resolve(ClientConfigurationQueryRepository.class);
    AuthorizationGrantedRepository authorizationGrantedRepository =
        container.resolve(AuthorizationGrantedRepository.class);
    AuthorizationCodeGrantRepository authorizationCodeGrantRepository =
        container.resolve(AuthorizationCodeGrantRepository.class);
    OAuthTokenCommandRepository oAuthTokenCommandRepository =
        container.resolve(OAuthTokenCommandRepository.class);
    OAuthSessionDelegate oAuthSessionDelegate = container.resolve(OAuthSessionDelegate.class);
    HttpRequestExecutor httpRequestExecutor = container.resolve(HttpRequestExecutor.class);
    return new DefaultOAuthProtocol(
        authorizationRequestRepository,
        authorizationServerConfigurationQueryRepository,
        clientConfigurationQueryRepository,
        authorizationGrantedRepository,
        authorizationCodeGrantRepository,
        oAuthTokenCommandRepository,
        oAuthSessionDelegate,
        httpRequestExecutor);
  }
}

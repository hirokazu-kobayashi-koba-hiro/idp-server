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

package org.idp.server.core.oidc.userinfo;

import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.token.repository.OAuthTokenQueryRepository;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.dependency.protocol.ProtocolProvider;

public class DefaultUserinfoProtocolProvider implements ProtocolProvider<UserinfoProtocol> {

  @Override
  public Class<UserinfoProtocol> type() {
    return UserinfoProtocol.class;
  }

  @Override
  public UserinfoProtocol provide(ApplicationComponentContainer container) {

    AuthorizationServerConfigurationQueryRepository
        authorizationServerConfigurationQueryRepository =
            container.resolve(AuthorizationServerConfigurationQueryRepository.class);
    ClientConfigurationQueryRepository clientConfigurationQueryRepository =
        container.resolve(ClientConfigurationQueryRepository.class);
    OAuthTokenQueryRepository oAuthTokenQueryRepository =
        container.resolve(OAuthTokenQueryRepository.class);

    return new DefaultUserinfoProtocol(
        oAuthTokenQueryRepository,
        authorizationServerConfigurationQueryRepository,
        clientConfigurationQueryRepository);
  }
}

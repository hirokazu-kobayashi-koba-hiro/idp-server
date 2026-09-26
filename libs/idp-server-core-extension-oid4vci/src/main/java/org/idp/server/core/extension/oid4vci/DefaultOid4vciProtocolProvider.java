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
package org.idp.server.core.extension.oid4vci;

import org.idp.server.core.extension.oid4vci.nonce.CredentialNonceRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.dependency.protocol.ProtocolProvider;

public class DefaultOid4vciProtocolProvider implements ProtocolProvider<Oid4vciProtocol> {

  @Override
  public Class<Oid4vciProtocol> type() {
    return Oid4vciProtocol.class;
  }

  @Override
  public Oid4vciProtocol provide(ApplicationComponentContainer container) {
    AuthorizationServerConfigurationQueryRepository
        authorizationServerConfigurationQueryRepository =
            container.resolve(AuthorizationServerConfigurationQueryRepository.class);
    CredentialNonceRepository credentialNonceRepository =
        container.resolve(CredentialNonceRepository.class);
    return new DefaultOid4vciProtocol(
        authorizationServerConfigurationQueryRepository,
        credentialNonceRepository,
        container.resolve(OAuthTokenQueryRepository.class),
        container.resolve(UserQueryRepository.class));
  }
}

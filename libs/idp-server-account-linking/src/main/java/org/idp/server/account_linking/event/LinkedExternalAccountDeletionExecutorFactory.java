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
package org.idp.server.account_linking.event;

import org.idp.server.account_linking.AccountLinkingConfigurationResolver;
import org.idp.server.account_linking.AccountLinkingTokenCipher;
import org.idp.server.account_linking.LinkedExternalAccountRevoker;
import org.idp.server.account_linking.gateway.ExternalIdpTokenGateway;
import org.idp.server.account_linking.repository.AccountLinkingSessionCommandRepository;
import org.idp.server.account_linking.repository.AccountLinkingSessionQueryRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountCommandRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.core.openid.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.openid.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.event.UserLifecycleEventExecutor;
import org.idp.server.core.openid.identity.event.UserLifecycleEventExecutorFactory;
import org.idp.server.platform.crypto.datakey.DataKeyService;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.http.HttpRequestExecutor;

public class LinkedExternalAccountDeletionExecutorFactory
    implements UserLifecycleEventExecutorFactory {

  @Override
  public UserLifecycleEventExecutor create(
      ApplicationComponentContainer applicationComponentContainer,
      AuthenticationDependencyContainer authenticationDependencyContainer) {

    LinkedExternalAccountRevoker revoker =
        new LinkedExternalAccountRevoker(
            new AccountLinkingConfigurationResolver(
                applicationComponentContainer.resolve(
                    FederationConfigurationQueryRepository.class)),
            new ExternalIdpTokenGateway(
                applicationComponentContainer.resolve(HttpRequestExecutor.class)),
            AccountLinkingTokenCipher.from(
                applicationComponentContainer.resolve(DataKeyService.class)));

    return new LinkedExternalAccountDeletionExecutor(
        applicationComponentContainer.resolve(LinkedExternalAccountQueryRepository.class),
        applicationComponentContainer.resolve(LinkedExternalAccountCommandRepository.class),
        applicationComponentContainer.resolve(AccountLinkingSessionQueryRepository.class),
        applicationComponentContainer.resolve(AccountLinkingSessionCommandRepository.class),
        revoker);
  }
}

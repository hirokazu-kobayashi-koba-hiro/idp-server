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
package org.idp.server.account_linking.plugin;

import org.idp.server.account_linking.AccountLinkingConfigurationResolver;
import org.idp.server.account_linking.AccountLinkingDataKeyPurpose;
import org.idp.server.account_linking.AccountLinkingFederationConfigurationRule;
import org.idp.server.account_linking.AccountLinkingTokenCipher;
import org.idp.server.account_linking.LinkedExternalAccountRevoker;
import org.idp.server.account_linking.TenantLinkedAccountCleanup;
import org.idp.server.account_linking.gateway.ExternalIdpTokenGateway;
import org.idp.server.account_linking.repository.AccountLinkingSessionCommandRepository;
import org.idp.server.account_linking.repository.AccountLinkingSessionQueryRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountCommandRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.core.openid.federation.FederationConfigurationRule;
import org.idp.server.core.openid.federation.FederationConfigurationRuleFactory;
import org.idp.server.core.openid.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.platform.crypto.datakey.DataKeyPurpose;
import org.idp.server.platform.crypto.datakey.DataKeyPurposeFactory;
import org.idp.server.platform.crypto.datakey.DataKeyService;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.multi_tenancy.tenant.TenantDeletionStep;
import org.idp.server.platform.multi_tenancy.tenant.TenantDeletionStepFactory;

/** What account linking contributes to the management of tenants, providers and data keys. */
public final class AccountLinkingPluginFactories {

  private AccountLinkingPluginFactories() {}

  public static class FederationRule implements FederationConfigurationRuleFactory {
    @Override
    public FederationConfigurationRule create(ApplicationComponentContainer container) {
      return new AccountLinkingFederationConfigurationRule(
          container.resolve(LinkedExternalAccountQueryRepository.class));
    }
  }

  public static class Purpose implements DataKeyPurposeFactory {
    @Override
    public DataKeyPurpose create(ApplicationComponentContainer container) {
      return new AccountLinkingDataKeyPurpose(
          container.resolve(LinkedExternalAccountQueryRepository.class));
    }
  }

  public static class TenantCleanup implements TenantDeletionStepFactory {
    @Override
    public TenantDeletionStep create(ApplicationComponentContainer container) {
      LinkedExternalAccountRevoker revoker =
          new LinkedExternalAccountRevoker(
              new AccountLinkingConfigurationResolver(
                  container.resolve(FederationConfigurationQueryRepository.class)),
              new ExternalIdpTokenGateway(container.resolve(HttpRequestExecutor.class)),
              AccountLinkingTokenCipher.from(container.resolve(DataKeyService.class)));
      return new TenantLinkedAccountCleanup(
          container.resolve(LinkedExternalAccountQueryRepository.class),
          container.resolve(LinkedExternalAccountCommandRepository.class),
          container.resolve(AccountLinkingSessionQueryRepository.class),
          container.resolve(AccountLinkingSessionCommandRepository.class),
          revoker);
    }
  }
}

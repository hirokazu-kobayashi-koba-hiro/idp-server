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
package org.idp.server.account_linking;

import java.util.List;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.core.openid.federation.FederationConfigurationNotFoundException;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Summarises the providers a client may link, for the user the client acts for.
 *
 * <p>Only the client's {@code linking_providers} are listed, in that order, so a client learns
 * nothing of the other providers the tenant configures.
 */
public class LinkingProviderSummaryService {

  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  AccountLinkingConfigurationResolver configurationResolver;
  LinkedExternalAccountQueryRepository accountQueryRepository;

  public LinkingProviderSummaryService(
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      AccountLinkingConfigurationResolver configurationResolver,
      LinkedExternalAccountQueryRepository accountQueryRepository) {
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.configurationResolver = configurationResolver;
    this.accountQueryRepository = accountQueryRepository;
  }

  public List<LinkingProviderSummary> summarize(
      Tenant tenant, RequestedClientId clientId, UserIdentifier userIdentifier) {
    ClientConfiguration client = clientConfigurationQueryRepository.get(tenant, clientId);
    List<LinkedExternalAccount> accounts = accountQueryRepository.findList(tenant, userIdentifier);

    return client.linkingProviders().stream()
        .map(ExternalIdpProvider::new)
        .map(provider -> summarize(tenant, provider, ProviderLinks.of(accounts, provider)))
        .toList();
  }

  /**
   * Available when the provider resolves as it would for starting a link: configured and enabled.
   */
  private LinkingProviderSummary summarize(
      Tenant tenant, ExternalIdpProvider provider, ProviderLinks links) {
    try {
      AccountLinkingProviderConfiguration configuration =
          configurationResolver.resolve(tenant, provider);
      return LinkingProviderSummary.available(provider, configuration, links);
    } catch (FederationConfigurationNotFoundException exception) {
      return LinkingProviderSummary.unavailable(provider, links);
    }
  }
}

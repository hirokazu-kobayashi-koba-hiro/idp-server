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

import org.idp.server.core.openid.federation.FederationType;
import org.idp.server.core.openid.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.openid.federation.sso.SsoProvider;
import org.idp.server.federation.sso.oidc.OidcSsoConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Reads the linking provider's settings.
 *
 * <p>Both views come from the same {@code federation_configurations} row for now. A dedicated
 * configuration type is what this should use: login and linking want different scopes and different
 * redirect URIs, so sharing a row makes the login consent screen advertise API scopes.
 */
public class AccountLinkingConfigurationResolver {

  static final FederationType FEDERATION_TYPE = new FederationType("oidc");

  FederationConfigurationQueryRepository configurationQueryRepository;

  public AccountLinkingConfigurationResolver(
      FederationConfigurationQueryRepository configurationQueryRepository) {
    this.configurationQueryRepository = configurationQueryRepository;
  }

  public OidcSsoConfiguration oidc(Tenant tenant, ExternalIdpProvider provider) {
    return configurationQueryRepository.get(
        tenant, FEDERATION_TYPE, new SsoProvider(provider.value()), OidcSsoConfiguration.class);
  }

  public AccountLinkingConfiguration linking(Tenant tenant, ExternalIdpProvider provider) {
    return configurationQueryRepository.get(
        tenant,
        FEDERATION_TYPE,
        new SsoProvider(provider.value()),
        AccountLinkingConfiguration.class);
  }
}

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
package org.idp.server.account_linking.verifier;

import org.idp.server.account_linking.AccountLinkingConfigurationResolver;
import org.idp.server.account_linking.ExternalIdpProvider;
import org.idp.server.account_linking.exception.AccountLinkingInvalidRequestException;
import org.idp.server.core.openid.federation.FederationConfigurationNotFoundException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Checks, when a link is started, that the named provider is configured and enabled.
 *
 * <p>{@code link} is the only step the RP calls as an API. Caught here, a misspelt or disabled
 * provider is a 400 the RP can handle; caught later, the browser is already on its way and can only
 * be sent to this server's error page.
 */
public class LinkingProviderVerifier {

  AccountLinkingConfigurationResolver configurationResolver;

  public LinkingProviderVerifier(AccountLinkingConfigurationResolver configurationResolver) {
    this.configurationResolver = configurationResolver;
  }

  public void verify(Tenant tenant, ExternalIdpProvider provider) {
    try {
      configurationResolver.resolve(tenant, provider);
    } catch (FederationConfigurationNotFoundException exception) {
      throw new AccountLinkingInvalidRequestException(
          "The linking provider is not configured or not enabled: " + provider.value());
    }
  }
}

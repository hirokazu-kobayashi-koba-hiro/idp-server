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

import java.util.Arrays;
import java.util.List;
import org.idp.server.account_linking.AccountLinkingConfigurationResolver;
import org.idp.server.account_linking.AccountLinkingProviderConfiguration;
import org.idp.server.account_linking.ExternalIdpProvider;
import org.idp.server.account_linking.exception.AccountLinkingInvalidRequestException;
import org.idp.server.account_linking.exception.AccountLinkingProviderNotAllowedException;
import org.idp.server.account_linking.exception.AccountLinkingProviderUnavailableException;
import org.idp.server.core.openid.federation.FederationConfigurationNotFoundException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Checks, when a link is started, that the client may link the named provider, that the provider is
 * configured and enabled, and that the requested scope stays within what the provider is configured
 * for.
 *
 * <p>{@code link} is the only step the RP calls as an API. Caught here, a misspelt or disabled
 * provider is a 400 the RP can handle; caught later, the browser is already on its way and can only
 * be sent to this server's error page.
 */
public class LinkingProviderVerifier {

  static final String OPENID = "openid";

  AccountLinkingConfigurationResolver configurationResolver;

  public LinkingProviderVerifier(AccountLinkingConfigurationResolver configurationResolver) {
    this.configurationResolver = configurationResolver;
  }

  /**
   * @param allowedProviders the client's {@code linking_providers}
   * @param requestedScope the scope the RP asks of the provider, or {@code null} for the provider's
   *     configured one
   */
  public void verify(
      Tenant tenant,
      ExternalIdpProvider provider,
      List<String> allowedProviders,
      String requestedScope) {

    // Checked before the provider is looked up, so a client cannot learn which providers the
    // tenant configures by trying names.
    if (!allowedProviders.contains(provider.value())) {
      throw new AccountLinkingProviderNotAllowedException(
          "The client may not start a link to this provider.");
    }

    AccountLinkingProviderConfiguration configuration;
    try {
      configuration = configurationResolver.resolve(tenant, provider);
    } catch (FederationConfigurationNotFoundException exception) {
      throw new AccountLinkingProviderUnavailableException(
          "The linking provider is not configured or not enabled: " + provider.value());
    }

    if (requestedScope == null || requestedScope.isBlank()) {
      return;
    }
    // The scope is asked of the provider in this server's name, under its external client. The
    // operator decides the most it may ask for; an RP can only narrow it.
    List<String> supported = configuration.oidc().scopesSupported();
    List<String> beyond =
        Arrays.stream(requestedScope.trim().split("\\s+"))
            .filter(scope -> supported == null || !supported.contains(scope))
            .toList();
    if (!beyond.isEmpty()) {
      throw new AccountLinkingInvalidRequestException(
          "The requested scope is not configured for this provider: " + String.join(" ", beyond));
    }
    // A provider configured for openid identifies the external account by the id_token's sub.
    // Without openid there is no id_token, so the link could not be told apart from a link to
    // another account, and the duplicate policy would not apply.
    if (supported != null
        && supported.contains(OPENID)
        && Arrays.stream(requestedScope.trim().split("\\s+")).noneMatch(OPENID::equals)) {
      throw new AccountLinkingInvalidRequestException(
          "The scope must include openid for this provider.");
    }
  }
}

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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.idp.server.account_linking.AccountLinkingConfigurationResolver;
import org.idp.server.account_linking.AccountLinkingProviderConfiguration;
import org.idp.server.account_linking.ExternalIdpProvider;
import org.idp.server.account_linking.exception.AccountLinkingProviderNotAllowedException;
import org.idp.server.account_linking.exception.AccountLinkingProviderUnavailableException;
import org.idp.server.core.openid.federation.FederationConfigurationNotFoundException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.junit.jupiter.api.Test;

class LinkingProviderVerifierTest {

  static final ExternalIdpProvider PROVIDER = new ExternalIdpProvider("example-idp");

  /** A tenant where the provider is missing or disabled. */
  static class NoProvider extends AccountLinkingConfigurationResolver {
    NoProvider() {
      super(null);
    }

    @Override
    public AccountLinkingProviderConfiguration resolve(
        Tenant tenant, ExternalIdpProvider provider) {
      throw new FederationConfigurationNotFoundException("not found");
    }
  }

  LinkingProviderVerifier verifier = new LinkingProviderVerifier(new NoProvider());

  @Test
  void answersAProviderTheTenantDoesNotOfferAsUnavailable() {
    assertThrows(
        AccountLinkingProviderUnavailableException.class,
        () -> verifier.verify(new Tenant(), PROVIDER, List.of("example-idp"), null));
  }

  @Test
  void refusesAProviderTheClientMayNotLinkBeforeLookingItUp() {
    // Otherwise the answer would tell the client which providers the tenant configures.
    assertThrows(
        AccountLinkingProviderNotAllowedException.class,
        () -> verifier.verify(new Tenant(), PROVIDER, List.of("another-idp"), null));
  }
}

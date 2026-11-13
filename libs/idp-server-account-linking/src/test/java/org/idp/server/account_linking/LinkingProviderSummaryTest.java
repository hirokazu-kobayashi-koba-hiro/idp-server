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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.federation.sso.oidc.OidcSsoConfiguration;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

class LinkingProviderSummaryTest {

  static final ExternalIdpProvider PROVIDER = new ExternalIdpProvider("google");

  static AccountLinkingProviderConfiguration configuration(Map<String, Object> linking) {
    return new AccountLinkingProviderConfiguration(
        new OidcSsoConfiguration(),
        JsonConverter.snakeCaseInstance().read(linking, AccountLinkingConfiguration.class));
  }

  static ProviderLinks links(int count) {
    return ProviderLinks.of(
        java.util.stream.IntStream.range(0, count)
            .mapToObj(
                i ->
                    new LinkedExternalAccount.Builder()
                        .provider(PROVIDER)
                        .accountAlias(new AccountAlias("google-" + i))
                        .build())
            .toList(),
        PROVIDER);
  }

  @Test
  void anAvailableProviderCarriesItsLimitAndTheUsersLinks() {
    Map<String, Object> map =
        LinkingProviderSummary.available(
                PROVIDER,
                configuration(Map.of("multiple_accounts", true, "max_accounts", 3)),
                links(2))
            .toMap();

    assertEquals("google", map.get("provider"));
    assertEquals(true, map.get("available"));
    assertEquals(true, map.get("multiple_accounts"));
    assertEquals(3, map.get("max_accounts"));
    assertEquals(2, map.get("linked_accounts"));
  }

  @Test
  void aProviderAllowingOneAccountHasALimitOfOne() {
    Map<String, Object> map =
        LinkingProviderSummary.available(PROVIDER, configuration(Map.of()), links(0)).toMap();

    assertEquals(false, map.get("multiple_accounts"));
    assertEquals(1, map.get("max_accounts"));
    assertEquals(0, map.get("linked_accounts"));
  }

  @Test
  void anUnavailableProviderStillCountsTheLinksLeftUnderIt() {
    Map<String, Object> map = LinkingProviderSummary.unavailable(PROVIDER, links(1)).toMap();

    assertEquals(false, map.get("available"));
    assertEquals(1, map.get("linked_accounts"));
    assertFalse(map.containsKey("multiple_accounts"));
    assertFalse(map.containsKey("max_accounts"));
    assertEquals(
        List.of("available", "linked_accounts", "provider"),
        map.keySet().stream().sorted().toList());
  }
}

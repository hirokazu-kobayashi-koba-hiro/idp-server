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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.federation.FederationConfiguration;
import org.junit.jupiter.api.Test;

class AccountLinkingFederationConfigurationRuleTest {

  AccountLinkingFederationConfigurationRule rule =
      new AccountLinkingFederationConfigurationRule(null);

  static Map<String, Object> usable() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("authorization_endpoint", "https://idp.example.com/authorize");
    payload.put("token_endpoint", "https://idp.example.com/token");
    payload.put("client_id", "client");
    payload.put("redirect_uri", "https://api.example.com/t1/v1/linking/callback/example-idp");
    payload.put("scopes_supported", List.of("account"));
    return payload;
  }

  static FederationConfiguration configuration(Map<String, Object> payload) {
    return new FederationConfiguration("id", "account-linking", "example-idp", payload);
  }

  @Test
  void acceptsAUsableConfiguration() {
    assertTrue(rule.problems(configuration(usable())).isEmpty());
  }

  @Test
  void namesEveryMissingEndpoint() {
    List<String> problems = rule.problems(configuration(new HashMap<>()));
    assertEquals(4, problems.size());
  }

  @Test
  void refusesAnotherProvidersCallback() {
    Map<String, Object> payload = usable();
    payload.put("redirect_uri", "https://api.example.com/t1/v1/linking/callback/other-idp");
    assertEquals(1, rule.problems(configuration(payload)).size());
  }

  @Test
  void refusesAMisspeltDuplicatePolicy() {
    Map<String, Object> payload = usable();
    payload.put("duplicate_link_policy", "rejct");
    assertEquals(1, rule.problems(configuration(payload)).size());
    payload.put("duplicate_link_policy", "ALLOW");
    assertTrue(rule.problems(configuration(payload)).isEmpty());
  }

  @Test
  void refusesMultipleAccountsThatIsNotABoolean() {
    Map<String, Object> payload = usable();
    payload.put("multiple_accounts", "true");
    assertEquals(1, rule.problems(configuration(payload)).size());
    payload.put("multiple_accounts", true);
    assertTrue(rule.problems(configuration(payload)).isEmpty());
  }

  @Test
  void refusesRevokeOnRelinkThatIsNotABoolean() {
    Map<String, Object> payload = usable();
    payload.put("revoke_on_relink", "true");
    assertEquals(1, rule.problems(configuration(payload)).size());
    payload.put("revoke_on_relink", true);
    assertTrue(rule.problems(configuration(payload)).isEmpty());
  }

  @Test
  void requiresTheIssuerAndKeysForOpenid() {
    Map<String, Object> payload = usable();
    payload.put("scopes_supported", List.of("openid", "profile"));
    assertEquals(2, rule.problems(configuration(payload)).size());
    payload.put("issuer", "https://idp.example.com");
    payload.put("jwks_uri", "https://idp.example.com/jwks");
    assertTrue(rule.problems(configuration(payload)).isEmpty());
  }

  @Test
  void keepsTheNameFixed() {
    assertFalse(rule.allowsRename());
  }
}

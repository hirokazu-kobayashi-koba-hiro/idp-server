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

package org.idp.server.authentication.interactors.password;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionResult;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.mapper.MappingRule;
import org.junit.jupiter.api.Test;

/**
 * Security regression for Issue #1497 / #1515 (CWE-287, identity switching).
 *
 * <p>On the 2nd factor, {@code user_resolve} must only enrich the already authenticated user with
 * descriptive profile claims / custom_properties. Identifiers, lifecycle and privilege fields must
 * never be patchable, otherwise a 2nd-factor credential submitted for a different account could
 * swap the session identity's attributes.
 */
class PasswordAuthenticationInteractorSecondFactorEnrichmentTest {

  PasswordAuthenticationInteractor interactor = new PasswordAuthenticationInteractor(null, null);
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  /** Build mapping rules from a JSON array so {@code static_value} selects the right overload. */
  private List<MappingRule> rules(String json) {
    return List.of(jsonConverter.read(json, MappingRule[].class));
  }

  @Test
  void dropsIdentityLifecycleAndPrivilegeTargetsButKeepsDescriptiveClaims() {
    // An attacker-influenced 2nd-factor mapping that tries to overwrite identity, lifecycle and
    // privileges, alongside legitimate enrichment (name + custom_properties).
    List<MappingRule> rules =
        rules(
            """
            [
              { "static_value": "attacker@evil.example.com", "to": "email" },
              { "static_value": "attacker", "to": "preferred_username" },
              { "static_value": "DEACTIVATED", "to": "status" },
              { "static_value": [ { "role_id": "admin", "role_name": "admin" } ], "to": "roles" },
              { "static_value": "ext-attacker-999", "to": "external_user_id" },
              { "static_value": "evil-idp", "to": "provider_id" },
              { "static_value": "Attacker Name", "to": "name" },
              { "static_value": "external_authenticated", "to": "custom_properties.auth_source" }
            ]
            """);

    AuthenticationInteractionRequest request =
        new AuthenticationInteractionRequest(Map.of("username", "victim", "password", "secret"));
    AuthenticationExecutionResult executionResult = AuthenticationExecutionResult.success(Map.of());

    User patch = interactor.buildSecondFactorEnrichmentPatch(request, executionResult, rules);

    // Inviolable on the 2nd factor: identifiers, email, lifecycle, privileges are dropped.
    assertFalse(patch.hasEmail(), "email must not be patchable on 2nd factor");
    assertFalse(patch.hasPreferredUsername(), "preferred_username must not be patchable");
    assertFalse(patch.hasStatus(), "status must not be patchable");
    assertFalse(patch.hasRoles(), "roles must not be patchable");
    // The patch carries no sub, so updateWith keeps the 1st-factor identity.
    assertTrue(patch.sub() == null || patch.sub().isEmpty(), "patch must not carry a sub");

    // Descriptive enrichment survives.
    assertTrue(patch.hasName());
    assertEquals("Attacker Name", patch.name());
    assertTrue(patch.hasCustomProperties());
    assertEquals("external_authenticated", patch.customPropertiesValue().get("auth_source"));
  }

  @Test
  void appliedToSessionUserPreservesFirstFactorIdentity() {
    // The session user established by the 1st factor.
    User sessionUser =
        new User()
            .setSub("victim-sub")
            .setPreferredUsername("victim")
            .setEmail("victim@example.com")
            .setName("Victim");

    List<MappingRule> rules =
        rules(
            """
            [
              { "static_value": "attacker@evil.example.com", "to": "email" },
              { "static_value": "attacker", "to": "preferred_username" },
              { "static_value": "external_authenticated", "to": "custom_properties.auth_source" }
            ]
            """);

    AuthenticationInteractionRequest request =
        new AuthenticationInteractionRequest(Map.of("username", "attacker"));
    AuthenticationExecutionResult executionResult = AuthenticationExecutionResult.success(Map.of());

    User patch = interactor.buildSecondFactorEnrichmentPatch(request, executionResult, rules);
    User merged = sessionUser.updateWith(patch);

    // Identity stays the victim's; only custom_properties is enriched.
    assertEquals("victim-sub", merged.sub());
    assertEquals("victim", merged.preferredUsername());
    assertEquals("victim@example.com", merged.email());
    assertEquals("external_authenticated", merged.customPropertiesValue().get("auth_source"));
  }
}

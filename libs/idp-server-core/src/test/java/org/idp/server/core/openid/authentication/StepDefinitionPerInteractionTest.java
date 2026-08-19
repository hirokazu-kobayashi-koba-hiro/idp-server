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

package org.idp.server.core.openid.authentication;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.authentication.policy.AuthenticationStepDefinition;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Step definitions resolved per interaction (#1813).
 *
 * <p>An interactor that holds several interactions under one method reports the same {@code
 * method()} whichever one ran — {@code external-api-authentication} returns {@code "external-api"}
 * for all of them. A definition keyed on the method alone therefore applies to every interaction in
 * the configuration, so {@code requires_user} could not be true for a 2nd-factor interaction and
 * false for the one that identifies the user.
 */
class StepDefinitionPerInteractionTest {

  private static final JsonConverter JSON = JsonConverter.snakeCaseInstance();

  /** Builds a transaction carrying only the policy, which is all the resolution reads. */
  private static AuthenticationTransaction transactionWith(String stepDefinitionsJson) {
    String policyJson =
        """
        {
          "success_conditions": { "any_of": [[{ "path": "$.x", "operation": "gte", "value": 1 }]] },
          "step_definitions": %s
        }
        """
            .formatted(stepDefinitionsJson);
    AuthenticationPolicy policy = JSON.read(policyJson, AuthenticationPolicy.class);
    return new AuthenticationTransaction(
        new AuthenticationTransactionIdentifier("tx"),
        null,
        new AuthenticationRequest(),
        policy,
        new AuthenticationTransactionAttributes());
  }

  @Nested
  class Resolution {

    @Test
    void narrowsToTheNamedInteraction() {
      AuthenticationTransaction transaction =
          transactionWith(
              """
              [
                { "method": "external-api", "interaction": "step-a", "requires_user": false },
                { "method": "external-api", "interaction": "step-b", "requires_user": true }
              ]
              """);

      assertFalse(
          transaction.getCurrentStepDefinition("external-api", "step-a").requiresUser(),
          "the interaction that identifies the user is a 1st factor");
      assertTrue(
          transaction.getCurrentStepDefinition("external-api", "step-b").requiresUser(),
          "the interaction that only adds a check is a 2nd factor");
    }

    @Test
    void treatsAMethodLevelDefinitionAsTheDefault() {
      // The shape the issue is really after: state the common case once, override the exception.
      AuthenticationTransaction transaction =
          transactionWith(
              """
              [
                { "method": "external-api", "requires_user": true },
                { "method": "external-api", "interaction": "step-a", "requires_user": false }
              ]
              """);

      assertFalse(transaction.getCurrentStepDefinition("external-api", "step-a").requiresUser());
      assertTrue(transaction.getCurrentStepDefinition("external-api", "step-b").requiresUser());
      assertTrue(transaction.getCurrentStepDefinition("external-api", "step-c").requiresUser());
    }

    @Test
    void appliesTheMethodLevelDefinitionRegardlessOfDeclarationOrder() {
      // The default must win by being unnamed, not by coming last.
      AuthenticationTransaction transaction =
          transactionWith(
              """
              [
                { "method": "external-api", "interaction": "step-a", "requires_user": false },
                { "method": "external-api", "requires_user": true }
              ]
              """);

      assertFalse(transaction.getCurrentStepDefinition("external-api", "step-a").requiresUser());
      assertTrue(transaction.getCurrentStepDefinition("external-api", "step-b").requiresUser());
    }

    @Test
    void resolvesNothingWhenOnlyOtherInteractionsAreNamed() {
      // No method-level default to fall back to. Callers treat null as "no constraint", which is
      // the same as having no step_definitions at all — pinned so the fallback is not mistaken for
      // an implicit default.
      AuthenticationTransaction transaction =
          transactionWith(
              """
              [{ "method": "external-api", "interaction": "step-a", "requires_user": true }]
              """);

      assertNull(transaction.getCurrentStepDefinition("external-api", "step-b"));
    }

    @Test
    void doesNotMatchAcrossMethods() {
      AuthenticationTransaction transaction =
          transactionWith(
              """
              [{ "method": "password", "interaction": "step-a", "requires_user": true }]
              """);

      assertNull(transaction.getCurrentStepDefinition("external-api", "step-a"));
    }
  }

  @Nested
  class BackwardCompatibility {

    @Test
    void keepsResolvingConfigurationsThatNameNoInteraction() {
      // Every stored configuration is this shape. The single-argument overload is what the other
      // interactors call, and it must keep behaving as it did.
      AuthenticationTransaction transaction =
          transactionWith(
              """
              [{ "method": "external-api", "order": 1, "requires_user": true }]
              """);

      assertTrue(transaction.getCurrentStepDefinition("external-api").requiresUser());
      assertTrue(transaction.getCurrentStepDefinition("external-api", null).requiresUser());
      assertTrue(transaction.getCurrentStepDefinition("external-api", "").requiresUser());
      assertTrue(transaction.getCurrentStepDefinition("external-api", "anything").requiresUser());
    }

    @Test
    void omitsTheKeyFromTheRepresentationWhenNotNamed() {
      // The management GET -> modify -> PUT round trip must not start emitting an empty interaction
      // for the configurations that do not use it.
      AuthenticationStepDefinition unnamed =
          JSON.read(
              "{\"method\":\"external-api\",\"requires_user\":true}",
              AuthenticationStepDefinition.class);

      assertFalse(unnamed.hasInteraction());
      assertFalse(unnamed.toMap().containsKey("interaction"));
    }

    @Test
    void carriesTheKeyThroughTheRepresentationWhenNamed() {
      AuthenticationStepDefinition named =
          JSON.read(
              "{\"method\":\"external-api\",\"interaction\":\"step-a\",\"requires_user\":false}",
              AuthenticationStepDefinition.class);

      assertTrue(named.hasInteraction());
      assertEquals("step-a", named.toMap().get("interaction"));
    }
  }
}

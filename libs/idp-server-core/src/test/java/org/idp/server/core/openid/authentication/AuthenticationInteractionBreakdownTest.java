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

import java.util.Map;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the per-interaction breakdown of authentication results (#1771).
 *
 * <p>{@code external-api-authentication} holds several interactions in one configuration, all
 * reached through the same endpoint path. Counting them together means a policy condition such as
 * {@code success_count >= 3} is satisfied by calling one interaction three times, so a required
 * interaction can be skipped and the authentication still completes.
 */
class AuthenticationInteractionBreakdownTest {

  private static final String TYPE = "external-api-authentication";

  private static AuthenticationInteractionRequestResult result(
      String interactionName, boolean success) {
    AuthenticationInteractionRequestResult result =
        new AuthenticationInteractionRequestResult(
            success
                ? AuthenticationInteractionStatus.SUCCESS
                : AuthenticationInteractionStatus.CLIENT_ERROR,
            new AuthenticationInteractionType(TYPE),
            OperationType.AUTHENTICATION,
            "external-api",
            Map.of(),
            DefaultSecurityEventType.external_api_authentication_success);
    result.setInteractionName(interactionName);
    return result;
  }

  private static AuthenticationInteractionResults resultsOf(
      AuthenticationInteractionRequestResult... requests) {
    AuthenticationInteractionResults results = new AuthenticationInteractionResults();
    for (AuthenticationInteractionRequestResult request : requests) {
      Map<String, AuthenticationInteractionResult> map = results.toMap();
      if (results.contains(TYPE)) {
        map.put(TYPE, results.get(TYPE).updateWith(request));
      } else {
        AuthenticationInteractionResult seeded =
            new AuthenticationInteractionResult(
                request.operationType().name(),
                request.method(),
                1,
                request.isSuccess() ? 1 : 0,
                request.isSuccess() ? 0 : 1,
                org.idp.server.platform.date.SystemDateTime.now(),
                new java.util.HashMap<>(
                    Map.of(
                        request.interactionName(),
                        AuthenticationInteractionResult.initialResultFor(request))));
        map.put(TYPE, seeded);
      }
      results = new AuthenticationInteractionResults(map);
    }
    return results;
  }

  @Nested
  class Counting {

    @Test
    void separatesInteractionsThatShareAType() {
      AuthenticationInteractionResults results =
          resultsOf(result("interaction-a", true), result("interaction-b", true));

      AuthenticationInteractionResult type = results.get(TYPE);
      assertEquals(1, type.interactions().get("interaction-a").successCount());
      assertEquals(1, type.interactions().get("interaction-b").successCount());
    }

    @Test
    void doesNotLetRepeatingOneInteractionLookLikeThree() {
      // The reason this exists: with a single total, three calls to interaction-a satisfy
      // "success_count >= 3" and interaction-b / interaction-c are never required to run.
      AuthenticationInteractionResults results =
          resultsOf(
              result("interaction-a", true),
              result("interaction-a", true),
              result("interaction-a", true));

      AuthenticationInteractionResult type = results.get(TYPE);
      assertEquals(3, type.successCount());
      assertEquals(3, type.interactions().get("interaction-a").successCount());
      assertNull(type.interactions().get("interaction-b"));
    }

    @Test
    void keepsTheTypeTotalAsTheSum() {
      // Conditions written before the breakdown existed read the total, so it has to stay a sum.
      AuthenticationInteractionResults results =
          resultsOf(
              result("interaction-a", true),
              result("interaction-b", false),
              result("interaction-b", true));

      AuthenticationInteractionResult type = results.get(TYPE);
      assertEquals(3, type.callCount());
      assertEquals(2, type.successCount());
      assertEquals(1, type.failureCount());
    }

    @Test
    void countsFailuresPerInteraction() {
      // Per-interaction attempt limits are the other half of #1771: a shared failure_count cannot
      // express "lock after 3 failures of interaction-b".
      AuthenticationInteractionResults results =
          resultsOf(result("interaction-a", true), result("interaction-b", false));

      AuthenticationInteractionResult type = results.get(TYPE);
      assertEquals(0, type.interactions().get("interaction-a").failureCount());
      assertEquals(1, type.interactions().get("interaction-b").failureCount());
    }
  }

  @Nested
  class PolicyPaths {

    /**
     * The breakdown is only useful if a policy condition can address it. Nothing in the policy
     * layer changed — {@code toMapAsObject()} becomes the JSONPath context as-is — so this checks
     * the path a configuration would actually write, one level deeper than the pinned hyphen case
     * in {@code JsonPathWrapperTest}.
     */
    @Test
    void addressesAnInteractionFromAConditionPath() {
      AuthenticationInteractionResults results =
          resultsOf(
              result("interaction-a", true),
              result("interaction-b", true),
              result("interaction-b", true));

      JsonNodeWrapper node = JsonNodeWrapper.fromMap(results.toMapAsObject());
      JsonPathWrapper jsonPath = new JsonPathWrapper(node.toJson());

      assertEquals(
          1,
          jsonPath.readAsInt(
              "$.external-api-authentication.interactions.interaction-a.success_count"));
      assertEquals(
          2,
          jsonPath.readAsInt(
              "$.external-api-authentication.interactions.interaction-b.success_count"));
      // The total remains addressable, which is what existing conditions read.
      assertEquals(3, jsonPath.readAsInt("$.external-api-authentication.success_count"));
    }

    @Test
    void anInteractionThatNeverRanIsAbsentRatherThanZero() {
      // A condition on a never-called interaction must not be satisfiable. readRaw returns null for
      // an unresolved path (#1646), and gte against null evaluates to false — so "required
      // interaction did not run" fails closed.
      AuthenticationInteractionResults results = resultsOf(result("interaction-a", true));

      JsonNodeWrapper node = JsonNodeWrapper.fromMap(results.toMapAsObject());
      JsonPathWrapper jsonPath = new JsonPathWrapper(node.toJson());

      assertNull(
          jsonPath.readRaw(
              "$.external-api-authentication.interactions.interaction-b.success_count"));
    }
  }

  @Nested
  class Serialization {

    @Test
    void roundTripsTheBreakdown() {
      AuthenticationInteractionResults results =
          resultsOf(result("interaction-a", true), result("interaction-b", false));

      AuthenticationInteractionResults restored =
          AuthenticationInteractionResults.fromMap(results.toStorageMap());

      AuthenticationInteractionResult type = restored.get(TYPE);
      assertEquals(1, type.interactions().get("interaction-a").successCount());
      assertEquals(1, type.interactions().get("interaction-b").failureCount());
    }

    @Test
    void isAbsentWhenNoInteractionWasNamed() {
      // Every other interactor is one authentication factor and names nothing, so its serialized
      // form has to be unchanged — a stored row from before this feature must read back the same.
      AuthenticationInteractionRequestResult unnamed =
          new AuthenticationInteractionRequestResult(
              AuthenticationInteractionStatus.SUCCESS,
              new AuthenticationInteractionType("password-authentication"),
              OperationType.AUTHENTICATION,
              "password",
              Map.of(),
              DefaultSecurityEventType.password_success);

      AuthenticationInteractionResult result =
          new AuthenticationInteractionResult(
                  "AUTHENTICATION",
                  "password",
                  0,
                  0,
                  0,
                  org.idp.server.platform.date.SystemDateTime.now())
              .updateWith(unnamed);

      assertFalse(result.hasInteractions());
      assertFalse(result.toMap().containsKey("interactions"));
    }

    @Test
    void readsRowsWrittenBeforeTheBreakdownExisted() {
      Map<String, Map<String, Object>> stored =
          Map.of(
              TYPE,
              Map.of(
                  "operation_type",
                  "AUTHENTICATION",
                  "method",
                  "external-api",
                  "call_count",
                  2,
                  "success_count",
                  2,
                  "failure_count",
                  0,
                  "interaction_time",
                  "2026-08-17T10:00:00"));

      AuthenticationInteractionResults restored = AuthenticationInteractionResults.fromMap(stored);

      AuthenticationInteractionResult type = restored.get(TYPE);
      assertEquals(2, type.successCount());
      assertFalse(type.hasInteractions());
    }
  }
}

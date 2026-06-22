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

package org.idp.server.core.openid.authentication.evaluator;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.AuthenticationInteractionResult;
import org.idp.server.core.openid.authentication.AuthenticationInteractionResults;
import org.idp.server.core.openid.authentication.policy.AuthenticationResultConditionConfig;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserRole;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Issue #1501: authentication policy conditions can reference the transaction user as {@code
 * $.user.*}. These tests cover the new behavior, backward compatibility of existing
 * interaction-result paths, the fail-safe allow-list projection, and the 2-arg overload delegation.
 */
class MfaConditionEvaluatorTest {

  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  private AuthenticationResultConditionConfig config(String anyOfJson) {
    return jsonConverter.read(
        "{\"any_of\":" + anyOfJson + "}", AuthenticationResultConditionConfig.class);
  }

  /** A single interaction result so {@code results.exists()} is true. */
  private AuthenticationInteractionResults passwordResults(int successCount, int failureCount) {
    Map<String, AuthenticationInteractionResult> values = new HashMap<>();
    values.put(
        "password-authentication",
        new AuthenticationInteractionResult(
            "authentication", "password", 1, successCount, failureCount, LocalDateTime.now()));
    return new AuthenticationInteractionResults(values);
  }

  private User registeredUser() {
    return new User().setSub("user-1").setStatus(UserStatus.REGISTERED);
  }

  @Nested
  @DisplayName("user attribute conditions ($.user.*)")
  class UserAttributeConditions {

    @Test
    @DisplayName("$.user.status eq REGISTERED is satisfied for a registered user")
    void statusMatches() {
      AuthenticationResultConditionConfig config =
          config("[[{\"path\":\"$.user.status\",\"operation\":\"eq\",\"value\":\"REGISTERED\"}]]");

      boolean result =
          MfaConditionEvaluator.isSuccessSatisfied(config, passwordResults(1, 0), registeredUser());

      assertTrue(result);
    }

    @Test
    @DisplayName("$.user.status eq REGISTERED is NOT satisfied for an initialized user")
    void statusDoesNotMatchInitialized() {
      AuthenticationResultConditionConfig config =
          config("[[{\"path\":\"$.user.status\",\"operation\":\"eq\",\"value\":\"REGISTERED\"}]]");
      User initialized = new User().setSub("user-2").setStatus(UserStatus.INITIALIZED);

      boolean result =
          MfaConditionEvaluator.isSuccessSatisfied(config, passwordResults(1, 0), initialized);

      assertFalse(result);
    }

    @Test
    @DisplayName(
        "$.user.status in [REGISTERED, IDENTITY_VERIFIED] matches either, but not INITIALIZED")
    void statusInList() {
      AuthenticationResultConditionConfig config =
          config(
              "[[{\"path\":\"$.user.status\",\"operation\":\"in\",\"value\":[\"REGISTERED\",\"IDENTITY_VERIFIED\"]}]]");

      assertTrue(
          MfaConditionEvaluator.isSuccessSatisfied(
              config, passwordResults(1, 0), registeredUser()));
      assertTrue(
          MfaConditionEvaluator.isSuccessSatisfied(
              config,
              passwordResults(1, 0),
              new User().setSub("user-iv").setStatus(UserStatus.IDENTITY_VERIFIED)));
      assertFalse(
          MfaConditionEvaluator.isSuccessSatisfied(
              config,
              passwordResults(1, 0),
              new User().setSub("user-3").setStatus(UserStatus.INITIALIZED)));
    }

    @Test
    @DisplayName("$.user.roles contains 'admin' matches a user assigned the admin role")
    void rolesContains() {
      AuthenticationResultConditionConfig config =
          config("[[{\"path\":\"$.user.roles\",\"operation\":\"contains\",\"value\":\"admin\"}]]");
      User admin =
          registeredUser()
              .setRoles(List.of(new UserRole("role-1", "admin"), new UserRole("role-2", "user")));
      User nonAdmin = registeredUser().setRoles(List.of(new UserRole("role-2", "user")));

      assertTrue(MfaConditionEvaluator.isSuccessSatisfied(config, passwordResults(1, 0), admin));
      assertFalse(
          MfaConditionEvaluator.isSuccessSatisfied(config, passwordResults(1, 0), nonAdmin));
    }

    @Test
    @DisplayName("$.user.custom_properties.* is referenceable (whole bag exposed)")
    void customPropertiesReferenceable() {
      AuthenticationResultConditionConfig config =
          config(
              "[[{\"path\":\"$.user.custom_properties.department\",\"operation\":\"eq\",\"value\":\"sales\"}]]");
      HashMap<String, Object> props = new HashMap<>();
      props.put("department", "sales");
      User user = registeredUser().setCustomProperties(props);

      assertTrue(MfaConditionEvaluator.isSuccessSatisfied(config, passwordResults(1, 0), user));
    }
  }

  @Nested
  @DisplayName("backward compatibility (existing interaction-result paths)")
  class BackwardCompatibility {

    @Test
    @DisplayName(
        "$.password-authentication.success_count gte 1 still evaluates via the 2-arg overload")
    void legacyPathTwoArg() {
      AuthenticationResultConditionConfig config =
          config(
              "[[{\"path\":\"$.password-authentication.success_count\",\"operation\":\"gte\",\"value\":1}]]");

      assertTrue(MfaConditionEvaluator.isSuccessSatisfied(config, passwordResults(1, 0)));
    }

    @Test
    @DisplayName("existing interaction-result paths are unaffected when a user is supplied")
    void legacyPathWithUser() {
      AuthenticationResultConditionConfig config =
          config(
              "[[{\"path\":\"$.password-authentication.success_count\",\"operation\":\"gte\",\"value\":1}]]");

      assertTrue(
          MfaConditionEvaluator.isSuccessSatisfied(
              config, passwordResults(1, 0), registeredUser()));
    }

    @Test
    @DisplayName("2-arg overload evaluates $.user.* against an empty user (no match, no error)")
    void userPathWithoutUserIsEmpty() {
      AuthenticationResultConditionConfig config =
          config("[[{\"path\":\"$.user.status\",\"operation\":\"eq\",\"value\":\"REGISTERED\"}]]");

      assertFalse(MfaConditionEvaluator.isSuccessSatisfied(config, passwordResults(1, 0)));
    }
  }

  @Nested
  @DisplayName("fail-safe allow-list projection (PolicyEvaluationUserContextCreator)")
  class FailSafeProjection {

    @Test
    @DisplayName("sensitive attributes are never exposed under $.user")
    void sensitiveExcluded() {
      User user =
          registeredUser()
              .setHashedPassword("$2a$10$abcdefghijklmnopqrstuv")
              .setVerifiedClaims(Map.of("claims", Map.of("given_name", "Alice")));

      Map<String, Object> context = PolicyEvaluationUserContextCreator.create(user);

      assertFalse(context.containsKey("hashed_password"));
      assertFalse(context.containsKey("raw_password"));
      assertFalse(context.containsKey("verified_claims"));
      assertFalse(context.containsKey("credentials"));
      // presence is surfaced as a boolean, never the secret itself
      assertEquals(true, context.get("has_password"));
    }

    @Test
    @DisplayName("a condition on an excluded attribute can never match (oracle is closed)")
    void excludedAttributeNeverMatches() {
      AuthenticationResultConditionConfig config =
          config(
              "[[{\"path\":\"$.user.verified_claims\",\"operation\":\"exists\",\"value\":true}]]");
      User user =
          registeredUser().setVerifiedClaims(Map.of("claims", Map.of("given_name", "Alice")));

      assertFalse(MfaConditionEvaluator.isSuccessSatisfied(config, passwordResults(1, 0), user));
    }

    @Test
    @DisplayName("a non-existent user yields an empty projection")
    void notFoundUserEmpty() {
      assertTrue(PolicyEvaluationUserContextCreator.create(User.notFound()).isEmpty());
    }
  }

  @Nested
  @DisplayName("failure / lock conditions")
  class FailureAndLock {

    @Test
    @DisplayName("failure condition can reference $.user.* (e.g. lock a disabled account)")
    void failureUserAttribute() {
      AuthenticationResultConditionConfig config =
          config("[[{\"path\":\"$.user.status\",\"operation\":\"eq\",\"value\":\"DISABLED\"}]]");
      User disabled = new User().setSub("user-9").setStatus(UserStatus.DISABLED);

      assertTrue(MfaConditionEvaluator.isFailureSatisfied(config, passwordResults(0, 1), disabled));
    }

    @Test
    @DisplayName("deny interaction short-circuits failure regardless of conditions")
    void denyShortCircuit() {
      Map<String, AuthenticationInteractionResult> values = new HashMap<>();
      values.put(
          "authentication-device-deny",
          new AuthenticationInteractionResult(
              "deny", "authentication-device", 1, 0, 1, LocalDateTime.now()));
      AuthenticationInteractionResults denyResults = new AuthenticationInteractionResults(values);
      AuthenticationResultConditionConfig config =
          config("[[{\"path\":\"$.user.status\",\"operation\":\"eq\",\"value\":\"REGISTERED\"}]]");

      assertTrue(MfaConditionEvaluator.isFailureSatisfied(config, denyResults, registeredUser()));
    }

    @Test
    @DisplayName("lock condition can reference $.user.* together with failure counts")
    void lockUserAttribute() {
      AuthenticationResultConditionConfig config =
          config(
              "[[{\"path\":\"$.password-authentication.failure_count\",\"operation\":\"gte\",\"value\":3},"
                  + "{\"path\":\"$.user.has_password\",\"operation\":\"eq\",\"value\":true}]]");
      User user = registeredUser().setHashedPassword("$2a$10$abcdefghijklmnopqrstuv");

      assertTrue(MfaConditionEvaluator.isLockedSatisfied(config, passwordResults(0, 3), user));
    }
  }
}

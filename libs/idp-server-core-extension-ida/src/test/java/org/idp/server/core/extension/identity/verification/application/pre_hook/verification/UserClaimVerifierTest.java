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

package org.idp.server.core.extension.identity.verification.application.pre_hook.verification;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfig;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationRequest;
import org.idp.server.core.openid.identity.User;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@code operation} extension of the {@code user_claim} pre_hook verifier (#1626).
 *
 * <p>The rule compares a request value (target) against a user attribute (expected) using a {@link
 * org.idp.server.platform.condition.ConditionOperation}. {@code operation} defaults to {@code eq},
 * preserving the original {@code Objects.equals} exact-match behavior. {@code in} expresses array
 * membership (request value must be an element of the user's collection); an unmatched value is
 * rejected (400 pre_hook_validation_failed) rather than silently passing.
 */
class UserClaimVerifierTest {

  private final UserClaimVerifier verifier = new UserClaimVerifier();

  private static final String DEVICE_IDS_PATH = "$.custom_properties.authentication_devices[*].id";

  @Test
  void inOperatorPassesWhenRequestValueIsMemberOfUserArray() {
    User user = userWithDevices("dev-1", "dev-2");
    IdentityVerificationRequest request = request(Map.of("id", "dev-1"));

    var result = verify(user, request, config(rule("in", "$.id", DEVICE_IDS_PATH)));

    assertTrue(result.isValid(), "request id is a member of the user's devices -> verified");
  }

  @Test
  void inOperatorFailsWhenRequestValueNotInUserArray() {
    User user = userWithDevices("dev-1", "dev-2");
    IdentityVerificationRequest request = request(Map.of("id", "dev-9"));

    var result = verify(user, request, config(rule("in", "$.id", DEVICE_IDS_PATH)));

    assertTrue(result.isError(), "unheld key must be rejected, not silently pass");
  }

  @Test
  void inOperatorFailsWhenUserHasNoArray() {
    User user = new User().setCustomProperties(new HashMap<>());
    IdentityVerificationRequest request = request(Map.of("id", "dev-1"));

    var result = verify(user, request, config(rule("in", "$.id", DEVICE_IDS_PATH)));

    assertTrue(result.isError(), "missing user collection -> membership cannot hold -> rejected");
  }

  @Test
  void ninOperatorFailsWhenRequestValueIsMember() {
    User user = userWithDevices("dev-1", "dev-2");
    IdentityVerificationRequest request = request(Map.of("id", "dev-1"));

    var result = verify(user, request, config(rule("nin", "$.id", DEVICE_IDS_PATH)));

    assertTrue(result.isError(), "nin asserts non-membership; a member must fail");
  }

  @Test
  void defaultOperationIsBackwardCompatibleExactMatch() {
    User user = userWithProperty("device_id", "dev-1");

    // omitting operation keeps the original Objects.equals behavior
    var matched =
        verify(
            user,
            request(Map.of("id", "dev-1")),
            config(rule(null, "$.id", "$.custom_properties.device_id")));
    assertTrue(matched.isValid());

    var mismatched =
        verify(
            user,
            request(Map.of("id", "dev-2")),
            config(rule(null, "$.id", "$.custom_properties.device_id")));
    assertTrue(mismatched.isError());
  }

  @Test
  void unknownOperationFailsAsMisconfiguration() {
    User user = userWithProperty("device_id", "dev-1");
    IdentityVerificationRequest request = request(Map.of("id", "dev-1"));

    var result =
        verify(user, request, config(rule("bogus", "$.id", "$.custom_properties.device_id")));

    assertTrue(result.isError());
    assertTrue(
        result.errors().stream().anyMatch(e -> e.contains("unknown operation")),
        "unknown operation must surface a distinct misconfiguration error");
  }

  // ----- helpers -----

  private IdentityVerificationRequest request(Map<String, Object> values) {
    return new IdentityVerificationRequest(values);
  }

  private static Map<String, Object> rule(String operation, String requestPath, String userPath) {
    Map<String, Object> rule = new HashMap<>();
    if (operation != null) {
      rule.put("operation", operation);
    }
    rule.put("request_json_path", requestPath);
    rule.put("user_claim_json_path", userPath);
    return rule;
  }

  @SafeVarargs
  private static IdentityVerificationConfig config(Map<String, Object>... rules) {
    Map<String, Object> details = new HashMap<>();
    details.put("verification_parameters", List.of(rules));
    return new IdentityVerificationConfig("user_claim", details);
  }

  private static User userWithDevices(String... ids) {
    List<Map<String, Object>> devices = new ArrayList<>();
    for (String id : ids) {
      devices.add(Map.of("id", id));
    }
    HashMap<String, Object> customProperties = new HashMap<>();
    customProperties.put("authentication_devices", devices);
    return new User().setCustomProperties(customProperties);
  }

  private static User userWithProperty(String key, Object value) {
    HashMap<String, Object> customProperties = new HashMap<>();
    customProperties.put(key, value);
    return new User().setCustomProperties(customProperties);
  }

  private IdentityVerificationApplicationRequestVerifiedResult verify(
      User user, IdentityVerificationRequest request, IdentityVerificationConfig config) {
    return verifier.verify(
        null,
        user,
        null,
        null,
        new IdentityVerificationType("ekyc"),
        new IdentityVerificationProcess("apply"),
        request,
        null,
        config,
        null);
  }
}

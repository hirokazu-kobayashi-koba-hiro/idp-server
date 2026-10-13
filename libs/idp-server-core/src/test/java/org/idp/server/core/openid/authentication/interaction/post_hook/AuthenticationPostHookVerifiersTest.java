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
package org.idp.server.core.openid.authentication.interaction.post_hook;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationPostHookConfig;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthenticationPostHookVerifiersTest {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  AuthenticationPostHookVerifiers verifiers = new AuthenticationPostHookVerifiers();

  Map<String, Object> context =
      Map.of(
          "execution_http_request",
          Map.of(
              "response_body",
              Map.of(
                  "user_id", "device-1", "devices", List.of("device-1", "device-2"), "count", 3)),
          "interaction",
          Map.of("device_id", "device-1"));

  @Test
  @DisplayName("succeeds without verifications")
  void succeedsWithoutVerifications() {
    assertTrue(verifiers.verifyAll(postHook("[]"), context).isSuccess());
  }

  @Test
  @DisplayName("succeeds when the values match")
  void succeedsWhenMatched() {
    assertTrue(
        verify(rule("$.execution_http_request.response_body.user_id", "$.interaction.device_id"))
            .isSuccess());
  }

  @Test
  @DisplayName("fails when the values do not match")
  void failsWhenUnmatched() {
    Map<String, Object> other =
        Map.of(
            "execution_http_request",
            Map.of("response_body", Map.of("user_id", "device-2")),
            "interaction",
            Map.of("device_id", "device-1"));
    assertTrue(
        verifiers
            .verifyAll(
                postHook(
                    match(
                        rule(
                            "$.execution_http_request.response_body.user_id",
                            "$.interaction.device_id"))),
                other)
            .isFailure());
  }

  @Test
  @DisplayName("fails when either value is missing")
  void failsWhenValueMissing() {
    assertTrue(
        verify(rule("$.execution_http_request.response_body.missing", "$.interaction.device_id"))
            .isFailure());
    assertTrue(verify(rule("$.interaction.device_id", "$.interaction.missing")).isFailure());
  }

  @Test
  @DisplayName("supports operations other than eq")
  void supportsOperations() {
    String in =
        """
        { "path": "$.interaction.device_id", "operation": "in", "target_path": "$.execution_http_request.response_body.devices" }
        """;
    assertTrue(verify(in).isSuccess());
  }

  @Test
  @DisplayName("fails when the type is unknown")
  void failsWithUnknownType() {
    String config = """
        [ { "type": "matches", "details": {} } ]
        """;
    assertTrue(verifiers.verifyAll(postHook(config), context).isFailure());
  }

  @Test
  @DisplayName("fails when the rules are empty or malformed")
  void failsWithMalformedRules() {
    assertTrue(verifiers.verifyAll(postHook(details("{ \"rules\": [] }")), context).isFailure());
    assertTrue(verifiers.verifyAll(postHook(details("{}")), context).isFailure());
    assertTrue(verifiers.verifyAll(postHook(details("{ \"rules\": \"x\" }")), context).isFailure());
    assertTrue(
        verifiers.verifyAll(postHook(details("{ \"rules\": [\"x\"] }")), context).isFailure());
  }

  @Test
  @DisplayName("fails when a rule lacks a path")
  void failsWithoutPath() {
    assertTrue(verify("{ \"path\": \"$.interaction.device_id\" }").isFailure());
  }

  @Test
  @DisplayName("fails when the operation is unknown")
  void failsWithUnknownOperation() {
    String rule =
        """
        { "path": "$.interaction.device_id", "operation": "equals", "target_path": "$.interaction.device_id" }
        """;
    assertTrue(verify(rule).isFailure());
  }

  @Test
  @DisplayName("fails instead of raising an error on an invalid JSONPath")
  void failsWithInvalidJsonPath() {
    assertTrue(verify(rule("$.[[[", "$.interaction.device_id")).isFailure());
  }

  @Test
  @DisplayName("fails instead of raising an error when the operation does not fit the values")
  void failsWhenOperationDoesNotFit() {
    String rule =
        """
        { "path": "$.interaction.device_id", "operation": "gt", "target_path": "$.execution_http_request.response_body.count" }
        """;
    assertTrue(verify(rule).isFailure());
  }

  @Test
  @DisplayName("keeps the verifications when the configuration is stored and read back")
  void keepsVerificationsOnRoundTrip() {
    String json =
        """
        {
          "id": "5ee62e7d-e1f0-4f38-b714-0c5d6a28d8f1",
          "type": "fido-uaf",
          "interactions": {
            "fido-uaf-authentication": {
              "post_hook": { "verifications": %s }
            }
          }
        }
        """
            .formatted(match(rule("$.a", "$.b")));
    AuthenticationConfiguration configuration =
        jsonConverter.read(json, AuthenticationConfiguration.class);

    AuthenticationConfiguration reread =
        jsonConverter.read(
            jsonConverter.write(configuration.toMap()), AuthenticationConfiguration.class);

    AuthenticationPostHookConfig postHook =
        reread.getAuthenticationConfig("fido-uaf-authentication").postHook();
    assertEquals(1, postHook.verifications().size());
    assertEquals("match", postHook.verifications().get(0).type());
    assertTrue(verifiers.verifyAll(postHook, Map.of("a", "x", "b", "x")).isSuccess());
  }

  private AuthenticationPostHookVerificationResult verify(String rule) {
    return verifiers.verifyAll(postHook(match(rule)), context);
  }

  private static String rule(String path, String targetPath) {
    return """
        { "path": "%s", "target_path": "%s" }
        """
        .formatted(path, targetPath);
  }

  private static String match(String rule) {
    return details("{ \"rules\": [ " + rule + " ] }");
  }

  private static String details(String details) {
    return "[ { \"type\": \"match\", \"details\": " + details + " } ]";
  }

  private AuthenticationPostHookConfig postHook(String verifications) {
    return jsonConverter.read(
        "{ \"verifications\": " + verifications + " }", AuthenticationPostHookConfig.class);
  }
}

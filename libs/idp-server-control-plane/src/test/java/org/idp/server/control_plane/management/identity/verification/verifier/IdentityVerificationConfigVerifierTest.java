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

package org.idp.server.control_plane.management.identity.verification.verifier;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityVerificationConfigVerifierTest {

  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  private static final Map<String, Object> BASIC_AUTH =
      Map.of("basic_auth", Map.of("username", "vendor", "password", "secret"));

  private Runnable verifierFor(Map<String, Object> processes) {
    Map<String, Object> payload =
        Map.of(
            "id", "0198e1c0-0000-7000-8000-00000000f967", "type", "probe", "processes", processes);
    IdentityVerificationConfiguration configuration =
        jsonConverter.read(payload, IdentityVerificationConfiguration.class);
    return () -> new IdentityVerificationConfigVerifier().verify(configuration);
  }

  @Test
  @DisplayName("caller の書き方が正しければ通る")
  void acceptsConsistentCallers() {
    assertDoesNotThrow(
        () ->
            verifierFor(
                    Map.of(
                        "apply", Map.of("caller", "end_user"),
                        "callback-result",
                            Map.of("caller", "external_service", "request", BASIC_AUTH),
                        "evaluate-result", Map.of("caller", "external_service"),
                        "legacy", Map.of(),
                        "legacy-callback", Map.of("request", BASIC_AUTH)))
                .run());
  }

  @Test
  @DisplayName("caller が知らない値なら拒否する")
  void rejectsUnknownCaller() {
    InvalidRequestException exception =
        assertThrows(
            InvalidRequestException.class,
            () -> verifierFor(Map.of("apply", Map.of("caller", "endUser"))).run());

    assertEquals(
        List.of("processes.apply.caller must be end_user or external_service: endUser"),
        exception.errorMessages());
  }

  @Test
  @DisplayName("caller が end_user なのに basic_auth があれば拒否する")
  void rejectsBasicAuthOnEndUserProcess() {
    InvalidRequestException exception =
        assertThrows(
            InvalidRequestException.class,
            () ->
                verifierFor(Map.of("apply", Map.of("caller", "end_user", "request", BASIC_AUTH)))
                    .run());

    assertEquals(1, exception.errorMessages().size());
    assertTrue(exception.errorMessages().get(0).startsWith("processes.apply.request.basic_auth"));
  }
}

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

package org.idp.server.core.openid.oauth.configuration;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Issue #1893: the iat windows are refused out of range (1 to 600 seconds). Issue #1902: so is the
 * longest a client assertion is accepted for.
 */
class AcceptableWindowViolationsTest {

  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @ParameterizedTest
  @CsvSource({
    // dpop, pop, violations
    "60,  60,  0",
    "1,   600, 0",
    "0,   60,  1",
    "-1,  60,  1",
    "60,  601, 1",
    "0,   0,   2",
  })
  void refusesWindowsOutOfRange(int dpop, int pop, int violations) {
    AuthorizationServerExtensionConfiguration extension =
        jsonConverter.read(
            "{\"dpop_proof_acceptable_window_seconds\":"
                + dpop
                + ",\"client_attestation_pop_acceptable_window_seconds\":"
                + pop
                + "}",
            AuthorizationServerExtensionConfiguration.class);

    assertEquals(violations, extension.acceptableWindowViolations().size());
  }

  @ParameterizedTest
  @CsvSource({
    // client_assertion_max_lifetime_seconds, violations
    "60,  0",
    "1,   0",
    "600, 0",
    "0,   1",
    "601, 1",
  })
  void refusesClientAssertionMaxLifetimeOutOfRange(int seconds, int violations) {
    AuthorizationServerExtensionConfiguration extension =
        jsonConverter.read(
            "{\"client_assertion_max_lifetime_seconds\":" + seconds + "}",
            AuthorizationServerExtensionConfiguration.class);

    assertEquals(violations, extension.acceptableWindowViolations().size());
  }

  @Test
  void defaultsToSixtySeconds() {
    AuthorizationServerExtensionConfiguration extension =
        jsonConverter.read("{}", AuthorizationServerExtensionConfiguration.class);

    assertEquals(Duration.ofSeconds(60), extension.dpopProofAcceptableWindow());
    assertEquals(Duration.ofSeconds(60), extension.clientAttestationPopAcceptableWindow());
    assertEquals(Duration.ofSeconds(60), extension.clientAssertionMaxLifetime());
    assertTrue(extension.acceptableWindowViolations().isEmpty());
  }
}

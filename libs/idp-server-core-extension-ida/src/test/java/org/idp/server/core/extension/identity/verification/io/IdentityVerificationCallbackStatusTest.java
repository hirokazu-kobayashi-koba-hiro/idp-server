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

package org.idp.server.core.extension.identity.verification.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * #1613: a failed callback execution must report the real downstream status rather than a blanket
 * 400. {@link IdentityVerificationCallbackStatus#fromStatusCode(int)} resolves the carried status
 * code, distinguishing client (4xx) from server (5xx) failures.
 */
class IdentityVerificationCallbackStatusTest {

  @Test
  void resolvesExactlyMappedCodes() {
    assertEquals(
        IdentityVerificationCallbackStatus.CLIENT_ERROR,
        IdentityVerificationCallbackStatus.fromStatusCode(400));
    assertEquals(
        IdentityVerificationCallbackStatus.UNAUTHORIZED,
        IdentityVerificationCallbackStatus.fromStatusCode(401));
    assertEquals(
        IdentityVerificationCallbackStatus.BAD_GATEWAY,
        IdentityVerificationCallbackStatus.fromStatusCode(502));
    assertEquals(
        IdentityVerificationCallbackStatus.SERVICE_UNAVAILABLE,
        IdentityVerificationCallbackStatus.fromStatusCode(503));
    assertEquals(
        IdentityVerificationCallbackStatus.GATEWAY_TIMEOUT,
        IdentityVerificationCallbackStatus.fromStatusCode(504));
  }

  @Test
  void fallsBackUnmappedClientCodesToClientError() {
    // 418 is not enumerated; any other 4xx collapses to CLIENT_ERROR (still client, not server).
    assertEquals(
        IdentityVerificationCallbackStatus.CLIENT_ERROR,
        IdentityVerificationCallbackStatus.fromStatusCode(418));
  }

  @Test
  void fallsBackUnmappedServerCodesToServerError() {
    assertEquals(
        IdentityVerificationCallbackStatus.SERVER_ERROR,
        IdentityVerificationCallbackStatus.fromStatusCode(599));
  }

  @Test
  void theCoreBugCaseExecutionFailureIsNotFlattenedTo400() {
    // The #1613 regression guard: a downstream 5xx must NOT be reported as CLIENT_ERROR (400).
    IdentityVerificationCallbackStatus resolved =
        IdentityVerificationCallbackStatus.fromStatusCode(502);
    assertEquals(502, resolved.statusCode());
    org.junit.jupiter.api.Assertions.assertNotEquals(
        IdentityVerificationCallbackStatus.CLIENT_ERROR, resolved);
  }
}

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

package org.idp.server.core.openid.federation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the status a federation callback reports to its caller (#1800).
 *
 * <p>This is the second of two places that flattened an upstream status to 400 / 500. Fixing only
 * {@code UserinfoExecutionStatus} changed nothing observable, because the value was re-flattened
 * here on the way out.
 */
class FederationInteractionStatusTest {

  @Nested
  class Classification {

    @Test
    void treatsEveryNon2xxAsAnError() {
      // isError() used to compare enum identity, which answered false for the codes added in
      // #1800. The caller branches on isError() alone, so a 429 slipping through as "not an error"
      // would let the callback continue into authorization with no user resolved.
      for (FederationInteractionStatus status : FederationInteractionStatus.values()) {
        if (status == FederationInteractionStatus.SUCCESS) {
          continue;
        }
        assertTrue(status.isError(), status.name());
        assertFalse(status.isSuccess(), status.name());
      }
    }

    @Test
    void successIsNotAnError() {
      assertTrue(FederationInteractionStatus.SUCCESS.isSuccess());
      assertFalse(FederationInteractionStatus.SUCCESS.isError());
    }
  }

  @Nested
  class FromStatusCode {

    @Test
    void keepsTheCodesItKnows() {
      assertEquals(
          FederationInteractionStatus.TOO_MANY_REQUESTS,
          FederationInteractionStatus.fromStatusCode(429));
      assertEquals(
          FederationInteractionStatus.SERVICE_UNAVAILABLE,
          FederationInteractionStatus.fromStatusCode(503));
      assertEquals(
          FederationInteractionStatus.UNAUTHORIZED,
          FederationInteractionStatus.fromStatusCode(401));
    }

    @Test
    void fallsBackToTheClassForUnlistedCodes() {
      assertEquals(
          FederationInteractionStatus.CLIENT_ERROR,
          FederationInteractionStatus.fromStatusCode(422));
      assertEquals(
          FederationInteractionStatus.SERVER_ERROR,
          FederationInteractionStatus.fromStatusCode(507));
    }

    @Test
    void collapsesAny2xxToSuccess() {
      // Pre-existing behaviour, kept: the callback has no notion of a partial success.
      assertEquals(
          FederationInteractionStatus.SUCCESS, FederationInteractionStatus.fromStatusCode(200));
      assertEquals(
          FederationInteractionStatus.SUCCESS, FederationInteractionStatus.fromStatusCode(204));
    }
  }
}

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

package org.idp.server.federation.sso.oidc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins that a resolved userinfo status survives instead of being flattened to 400 / 500 (#1800).
 *
 * <p>The classification tests are the load-bearing ones. The predicates used to compare enum
 * identity, which was correct only while there was exactly one 4xx and one 5xx constant; adding
 * codes without changing them would have made {@code isError()} answer false for a 429, and a
 * failed userinfo request read as a successful one.
 */
class UserinfoExecutionStatusTest {

  @Nested
  class Classification {

    @Test
    void everyAddedClientErrorCountsAsAClientError() {
      for (UserinfoExecutionStatus status :
          new UserinfoExecutionStatus[] {
            UserinfoExecutionStatus.CLIENT_ERROR,
            UserinfoExecutionStatus.UNAUTHORIZED,
            UserinfoExecutionStatus.FORBIDDEN,
            UserinfoExecutionStatus.NOT_FOUND,
            UserinfoExecutionStatus.REQUEST_TIMEOUT,
            UserinfoExecutionStatus.CONFLICT,
            UserinfoExecutionStatus.TOO_MANY_REQUESTS
          }) {
        assertTrue(status.isClientError(), status.name());
        assertFalse(status.isServerError(), status.name());
        assertTrue(status.isError(), status.name());
      }
    }

    @Test
    void everyAddedServerErrorCountsAsAServerError() {
      for (UserinfoExecutionStatus status :
          new UserinfoExecutionStatus[] {
            UserinfoExecutionStatus.SERVER_ERROR,
            UserinfoExecutionStatus.BAD_GATEWAY,
            UserinfoExecutionStatus.SERVICE_UNAVAILABLE,
            UserinfoExecutionStatus.GATEWAY_TIMEOUT
          }) {
        assertTrue(status.isServerError(), status.name());
        assertFalse(status.isClientError(), status.name());
        assertTrue(status.isError(), status.name());
      }
    }

    @Test
    void okIsNeitherAnErrorNorAFailure() {
      assertTrue(UserinfoExecutionStatus.OK.isOk());
      assertFalse(UserinfoExecutionStatus.OK.isError());
      assertFalse(UserinfoExecutionStatus.OK.isClientError());
      assertFalse(UserinfoExecutionStatus.OK.isServerError());
    }
  }

  @Nested
  class FromStatusCode {

    @Test
    void keepsTheCodesItKnows() {
      assertEquals(
          UserinfoExecutionStatus.TOO_MANY_REQUESTS, UserinfoExecutionStatus.fromStatusCode(429));
      assertEquals(
          UserinfoExecutionStatus.SERVICE_UNAVAILABLE, UserinfoExecutionStatus.fromStatusCode(503));
    }

    @Test
    void collapsesAny2xxToOk() {
      // Kept consistent with FederationInteractionStatus: without this a 204 would land in
      // SERVER_ERROR, and error(int, Map) is public enough that a 2xx will eventually reach it.
      assertEquals(UserinfoExecutionStatus.OK, UserinfoExecutionStatus.fromStatusCode(200));
      assertEquals(UserinfoExecutionStatus.OK, UserinfoExecutionStatus.fromStatusCode(204));
    }

    @Test
    void fallsBackToTheClassForUnlistedCodes() {
      // Unlisted codes must degrade to their class rather than throw: an upstream is free to answer
      // anything, and losing the 4xx / 5xx distinction is worse than losing the exact code.
      assertEquals(
          UserinfoExecutionStatus.CLIENT_ERROR, UserinfoExecutionStatus.fromStatusCode(422));
      assertEquals(
          UserinfoExecutionStatus.SERVER_ERROR, UserinfoExecutionStatus.fromStatusCode(507));
    }
  }

  @Nested
  class ResultFactory {

    @Test
    void carriesTheResolvedStatusThroughTheResult() {
      UserinfoExecutionResult result = UserinfoExecutionResult.error(429, Map.of("error", "busy"));

      assertEquals(429, result.statusCode());
      assertTrue(result.isError());
      // The interactor branches on isError() alone, so a 429 reaching it as "not an error" would
      // let an empty userinfo response flow into user resolution.
      assertFalse(result.isSuccess());
    }

    @Test
    void stillFailsForACodeItDoesNotKnow() {
      UserinfoExecutionResult result = UserinfoExecutionResult.error(418, Map.of());

      assertTrue(result.isError());
      assertEquals(400, result.statusCode());
    }
  }
}

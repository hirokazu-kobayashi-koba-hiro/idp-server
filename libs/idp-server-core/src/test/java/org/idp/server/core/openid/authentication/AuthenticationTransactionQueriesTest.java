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
import java.util.UUID;
import org.idp.server.platform.exception.BadRequestException;
import org.junit.jupiter.api.Test;

/** Issue #1840: クエリパラメータが実際に読まれる名前で解釈されることを確認する。 */
class AuthenticationTransactionQueriesTest {

  @Test
  void idAsUuidConvertsTheIdParameter() {
    String id = UUID.randomUUID().toString();
    AuthenticationTransactionQueries queries =
        new AuthenticationTransactionQueries(Map.of("id", id));

    assertTrue(queries.hasId());
    assertEquals(UUID.fromString(id), queries.idAsUuid());
  }

  @Test
  void idAsUuidRejectsMalformedValue() {
    AuthenticationTransactionQueries queries =
        new AuthenticationTransactionQueries(Map.of("id", "not-a-uuid"));

    assertThrows(BadRequestException.class, queries::idAsUuid);
  }

  @Test
  void deviceIdAsUuidConvertsDeviceIdNotClientId() {
    String deviceId = UUID.randomUUID().toString();
    AuthenticationTransactionQueries queries =
        new AuthenticationTransactionQueries(
            Map.of("device_id", deviceId, "client_id", "clientSecretPost"));

    assertTrue(queries.hasDeviceId());
    assertEquals(UUID.fromString(deviceId), queries.deviceIdAsUuid());
  }

  @Test
  void deviceIdAsUuidDoesNotDependOnClientIdBeingPresent() {
    String deviceId = UUID.randomUUID().toString();
    AuthenticationTransactionQueries queries =
        new AuthenticationTransactionQueries(Map.of("device_id", deviceId));

    assertEquals(UUID.fromString(deviceId), queries.deviceIdAsUuid());
  }

  @Test
  void excludeExpiredDefaultsToTrueWhenNotSpecified() {
    AuthenticationTransactionQueries queries =
        new AuthenticationTransactionQueries(Map.of("flow", "ciba"));

    assertTrue(queries.isExcludeExpired());
  }

  @Test
  void excludeExpiredFollowsTheSpecifiedValue() {
    assertFalse(
        new AuthenticationTransactionQueries(Map.of("exclude_expired", "false"))
            .isExcludeExpired());
    assertTrue(
        new AuthenticationTransactionQueries(Map.of("exclude_expired", "true")).isExcludeExpired());
  }
}

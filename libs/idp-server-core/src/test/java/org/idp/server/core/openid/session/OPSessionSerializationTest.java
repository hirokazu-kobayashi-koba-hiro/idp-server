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

package org.idp.server.core.openid.session;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Verifies OPSession JSON serialization round-trip. This is critical for the Jackson 2→3 migration
 * because OPSession is persisted as JSON in the session store (Redis/InMemory) via
 * JsonConverter.snakeCaseInstance().
 */
class OPSessionSerializationTest {

  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Test
  void roundTripsWithJsonConverter() {
    OPSession original = createTestSession();

    String json = jsonConverter.write(original);
    OPSession restored = jsonConverter.read(json, OPSession.class);

    assertEquals(original.id().value(), restored.id().value());
    assertEquals(original.tenantId().value(), restored.tenantId().value());
    assertEquals(original.acr(), restored.acr());
    assertEquals(original.amr(), restored.amr());
    assertEquals(original.ipAddress(), restored.ipAddress());
    assertEquals(original.userAgent(), restored.userAgent());
    assertEquals(original.status(), restored.status());
  }

  @Test
  void serializesInstantFields() {
    OPSession original = createTestSession();

    String json = jsonConverter.write(original);
    OPSession restored = jsonConverter.read(json, OPSession.class);

    assertEquals(original.authTime(), restored.authTime());
    assertEquals(original.createdAt(), restored.createdAt());
    assertEquals(original.expiresAt(), restored.expiresAt());
    assertEquals(original.lastAccessedAt(), restored.lastAccessedAt());
  }

  @Test
  void serializesInteractionResults() {
    Map<String, Map<String, Object>> interactionResults =
        Map.of("password", Map.of("verified", true, "method", "bcrypt"));
    OPSession original = createSessionWithInteractionResults(interactionResults);

    String json = jsonConverter.write(original);
    OPSession restored = jsonConverter.read(json, OPSession.class);

    assertTrue(restored.hasInteractionResults());
    Map<String, Object> passwordResult = restored.interactionResults().get("password");
    assertNotNull(passwordResult);
    assertEquals(true, passwordResult.get("verified"));
    assertEquals("bcrypt", passwordResult.get("method"));
  }

  @Test
  void serializesTerminatedSession() {
    OPSession original = createTestSession();
    original.terminate(TerminationReason.USER_LOGOUT);

    String json = jsonConverter.write(original);
    OPSession restored = jsonConverter.read(json, OPSession.class);

    assertEquals(SessionStatus.TERMINATED, restored.status());
    assertNotNull(restored.terminatedAt());
    assertEquals(TerminationReason.USER_LOGOUT, restored.terminationReason());
  }

  @Test
  void jsonContainsSnakeCaseFieldNames() {
    OPSession original = createTestSession();

    String json = jsonConverter.write(original);

    assertTrue(json.contains("\"tenant_id\""), "should use snake_case: " + json);
    assertTrue(json.contains("\"auth_time\""), "should use snake_case: " + json);
    assertTrue(json.contains("\"browser_state\""), "should use snake_case: " + json);
    assertTrue(json.contains("\"created_at\""), "should use snake_case: " + json);
    assertTrue(json.contains("\"expires_at\""), "should use snake_case: " + json);
    assertTrue(json.contains("\"last_accessed_at\""), "should use snake_case: " + json);
    assertTrue(json.contains("\"ip_address\""), "should use snake_case: " + json);
    assertTrue(json.contains("\"user_agent\""), "should use snake_case: " + json);
  }

  @Test
  void deserializesFromMinimalJson() {
    String json = "{\"id\":{\"value\":\"ops_test123\"},\"status\":\"ACTIVE\"}";

    OPSession restored = jsonConverter.read(json, OPSession.class);

    assertEquals("ops_test123", restored.id().value());
    assertEquals(SessionStatus.ACTIVE, restored.status());
  }

  @Nested
  class BrowserStateRoundTrip {

    @Test
    void roundTripsWithJsonConverter() {
      BrowserState original = BrowserState.generate();

      String json = jsonConverter.write(original);
      BrowserState restored = jsonConverter.read(json, BrowserState.class);

      assertEquals(original.value(), restored.value());
    }
  }

  @Nested
  class SessionStatusRoundTrip {

    @Test
    void roundTripsAllValues() {
      for (SessionStatus status : SessionStatus.values()) {
        String json = jsonConverter.write(status);
        SessionStatus restored = jsonConverter.read(json, SessionStatus.class);

        assertEquals(status, restored);
      }
    }
  }

  @Nested
  class TerminationReasonRoundTrip {

    @Test
    void roundTripsAllValues() {
      for (TerminationReason reason : TerminationReason.values()) {
        String json = jsonConverter.write(reason);
        TerminationReason restored = jsonConverter.read(json, TerminationReason.class);

        assertEquals(reason, restored);
      }
    }
  }

  private OPSession createTestSession() {
    return createSessionWithInteractionResults(Map.of());
  }

  private OPSession createSessionWithInteractionResults(
      Map<String, Map<String, Object>> interactionResults) {
    Instant now = Instant.parse("2026-04-18T10:00:00Z");
    return new OPSession(
        new OPSessionIdentifier("ops_test-session-id"),
        new TenantIdentifier("tenant-001"),
        new User(),
        now,
        "urn:idp:acr:password",
        List.of("pwd"),
        interactionResults,
        new BrowserState("browser-state-value"),
        now,
        now.plusSeconds(3600),
        now,
        "192.168.1.1",
        "Mozilla/5.0");
  }
}

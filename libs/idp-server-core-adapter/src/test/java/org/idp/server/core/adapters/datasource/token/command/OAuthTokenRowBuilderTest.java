/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.adapters.datasource.token.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.junit.jupiter.api.Test;

class OAuthTokenRowBuilderTest {

  @Test
  void stringifyString() {
    assertEquals("abc", OAuthTokenRowBuilder.stringify("abc"));
  }

  @Test
  void stringifyEmptyString() {
    assertEquals("", OAuthTokenRowBuilder.stringify(""));
  }

  @Test
  void stringifyNull() {
    assertNull(OAuthTokenRowBuilder.stringify(null));
  }

  @Test
  void stringifyUuidUsesCanonicalForm() {
    UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    assertEquals("123e4567-e89b-12d3-a456-426614174000", OAuthTokenRowBuilder.stringify(uuid));
  }

  @Test
  void stringifyLocalDateTimeProducesIsoForm() {
    LocalDateTime ldt = LocalDateTime.of(2026, 6, 10, 14, 30, 45);
    assertEquals("2026-06-10T14:30:45", OAuthTokenRowBuilder.stringify(ldt));
  }

  /**
   * Round-trip guarantee: the {@code String} produced by {@code stringify(LocalDateTime)} must be
   * parseable back into the same {@code LocalDateTime} by {@link LocalDateTimeParser}. This is the
   * contract that lets the cache map (built by INSERT) be safely consumed by {@code
   * query.ModelConverter}.
   */
  @Test
  void stringifyLocalDateTimeRoundTripsThroughParser() {
    LocalDateTime original = LocalDateTime.of(2026, 6, 10, 14, 30, 45, 123_456_789);
    String stringified = OAuthTokenRowBuilder.stringify(original);
    LocalDateTime parsed = LocalDateTimeParser.parse(stringified);
    assertEquals(original, parsed);
  }

  @Test
  void stringifyInteger() {
    assertEquals("42", OAuthTokenRowBuilder.stringify(42));
  }

  @Test
  void addPopulatesParamsAndRow() {
    List<Object> params = new ArrayList<>();
    Map<String, String> row = new LinkedHashMap<>();
    OAuthTokenRowBuilder.add(params, row, "client_id", "client-001");
    assertEquals(List.of("client-001"), params);
    assertEquals("client-001", row.get("client_id"));
  }

  @Test
  void addPreservesNullInRow() {
    List<Object> params = new ArrayList<>();
    Map<String, String> row = new LinkedHashMap<>();
    OAuthTokenRowBuilder.add(params, row, "user_id", null);
    assertEquals(1, params.size());
    assertNull(params.get(0));
    // The cache map records null explicitly so that ModelConverter can detect missing fields.
    assertNull(row.get("user_id"));
  }

  @Test
  void addStringifiesUuidIntoRowButKeepsTypeInParams() {
    List<Object> params = new ArrayList<>();
    Map<String, String> row = new LinkedHashMap<>();
    UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    OAuthTokenRowBuilder.add(params, row, "tenant_id", uuid);
    // JDBC params keep the typed UUID (so SqlExecutor can call setObject for ::uuid binding).
    assertEquals(uuid, params.get(0));
    // The row uses the canonical String for cache key/value consumption.
    assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", row.get("tenant_id"));
  }

  @Test
  void addStringifiesLocalDateTimeIntoRowButKeepsTypeInParams() {
    List<Object> params = new ArrayList<>();
    Map<String, String> row = new LinkedHashMap<>();
    LocalDateTime ldt = LocalDateTime.of(2026, 6, 10, 0, 0, 0);
    OAuthTokenRowBuilder.add(params, row, "expires_at", ldt);
    assertEquals(ldt, params.get(0));
    assertEquals("2026-06-10T00:00", row.get("expires_at"));
  }
}

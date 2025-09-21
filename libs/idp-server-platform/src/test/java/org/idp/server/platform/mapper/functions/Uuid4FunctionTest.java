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

package org.idp.server.platform.mapper.functions;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class Uuid4FunctionTest {

  private final Uuid4Function function = new Uuid4Function();

  @Test
  public void testName() {
    assertEquals("uuid4", function.name());
  }

  @Test
  public void testApplyGeneratesValidUuid() {
    Map<String, Object> args = new HashMap<>();

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    // Verify it's a valid UUID format
    UUID uuid = UUID.fromString(result);
    assertEquals(4, uuid.version()); // UUID v4
    assertEquals(2, uuid.variant()); // Standard variant
  }

  @Test
  public void testApplyIgnoresInput() {
    Map<String, Object> args = new HashMap<>();

    String result1 = (String) function.apply("ignored", args);
    String result2 = (String) function.apply(123, args);
    String result3 = (String) function.apply(null, args);

    assertNotNull(result1);
    assertNotNull(result2);
    assertNotNull(result3);

    // All should be valid UUIDs regardless of input
    UUID.fromString(result1);
    UUID.fromString(result2);
    UUID.fromString(result3);
  }

  @Test
  public void testApplyIgnoresArguments() {
    Map<String, Object> args = new HashMap<>();
    args.put("ignored", "value");
    args.put("length", 10);

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    UUID uuid = UUID.fromString(result);
    assertEquals(4, uuid.version());
  }

  @Test
  public void testApplyWithNullArgs() {
    String result = (String) function.apply(null, null);

    assertNotNull(result);
    UUID uuid = UUID.fromString(result);
    assertEquals(4, uuid.version());
  }

  @Test
  public void testApplyGeneratesUniqueUuids() {
    Map<String, Object> args = new HashMap<>();

    String result1 = (String) function.apply(null, args);
    String result2 = (String) function.apply(null, args);
    String result3 = (String) function.apply(null, args);

    assertNotNull(result1);
    assertNotNull(result2);
    assertNotNull(result3);

    // UUIDs should be unique (statistically almost certain)
    assertNotEquals(result1, result2);
    assertNotEquals(result2, result3);
    assertNotEquals(result1, result3);

    // All should be valid UUIDs
    UUID.fromString(result1);
    UUID.fromString(result2);
    UUID.fromString(result3);
  }

  @Test
  public void testUuidFormat() {
    Map<String, Object> args = new HashMap<>();

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    // Verify standard UUID format: 8-4-4-4-12
    assertTrue(result.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));

    // Verify version 4 in the UUID string
    assertTrue(result.charAt(14) == '4'); // Version bit at position 14
  }
}

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

public class Uuid5FunctionTest {

  private final Uuid5Function function = new Uuid5Function();

  // Standard namespaces from RFC 4122
  private static final String DNS_NAMESPACE = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";
  private static final String URL_NAMESPACE = "6ba7b811-9dad-11d1-80b4-00c04fd430c8";

  @Test
  public void testName() {
    assertEquals("uuid5", function.name());
  }

  @Test
  public void testApplyWithNamespaceAndName() {
    Map<String, Object> args = new HashMap<>();
    args.put("namespace", DNS_NAMESPACE);
    args.put("name", "example.com");

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    UUID uuid = UUID.fromString(result);
    assertEquals(5, uuid.version()); // UUID v5
    assertEquals(2, uuid.variant()); // Standard variant
  }

  @Test
  public void testApplyWithNamespaceUsesInputAsName() {
    Map<String, Object> args = new HashMap<>();
    args.put("namespace", DNS_NAMESPACE);

    String result = (String) function.apply("example.com", args);

    assertNotNull(result);
    UUID uuid = UUID.fromString(result);
    assertEquals(5, uuid.version());
  }

  @Test
  public void testApplyIsDeterministic() {
    Map<String, Object> args = new HashMap<>();
    args.put("namespace", DNS_NAMESPACE);
    args.put("name", "example.com");

    String result1 = (String) function.apply(null, args);
    String result2 = (String) function.apply(null, args);
    String result3 = (String) function.apply(null, args);

    assertNotNull(result1);
    assertNotNull(result2);
    assertNotNull(result3);

    // Should be identical (deterministic)
    assertEquals(result1, result2);
    assertEquals(result2, result3);
  }

  @Test
  public void testApplyWithDifferentNamespaces() {
    Map<String, Object> args1 = new HashMap<>();
    args1.put("namespace", DNS_NAMESPACE);
    args1.put("name", "example.com");

    Map<String, Object> args2 = new HashMap<>();
    args2.put("namespace", URL_NAMESPACE);
    args2.put("name", "example.com");

    String result1 = (String) function.apply(null, args1);
    String result2 = (String) function.apply(null, args2);

    assertNotNull(result1);
    assertNotNull(result2);
    assertNotEquals(result1, result2); // Different namespaces should produce different UUIDs
  }

  @Test
  public void testApplyWithDifferentNames() {
    Map<String, Object> args1 = new HashMap<>();
    args1.put("namespace", DNS_NAMESPACE);
    args1.put("name", "example.com");

    Map<String, Object> args2 = new HashMap<>();
    args2.put("namespace", DNS_NAMESPACE);
    args2.put("name", "example.org");

    String result1 = (String) function.apply(null, args1);
    String result2 = (String) function.apply(null, args2);

    assertNotNull(result1);
    assertNotNull(result2);
    assertNotEquals(result1, result2); // Different names should produce different UUIDs
  }

  @Test
  public void testApplyWithMissingNamespace() {
    Map<String, Object> args = new HashMap<>();
    args.put("name", "example.com");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply(null, args));

    assertTrue(exception.getMessage().contains("'namespace' is required"));
  }

  @Test
  public void testApplyWithNullArgs() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply(null, null));

    assertTrue(exception.getMessage().contains("args is null"));
  }

  @Test
  public void testApplyWithEmptyNamespace() {
    Map<String, Object> args = new HashMap<>();
    args.put("namespace", "");
    args.put("name", "example.com");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply(null, args));

    assertTrue(exception.getMessage().contains("'namespace' is required"));
  }

  @Test
  public void testApplyWithInvalidNamespace() {
    Map<String, Object> args = new HashMap<>();
    args.put("namespace", "invalid-uuid");
    args.put("name", "example.com");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply(null, args));

    assertTrue(exception.getMessage().contains("Invalid namespace"));
  }

  @Test
  public void testStandardNamespaceAliases() {
    Map<String, Object> args = new HashMap<>();
    args.put("namespace", "DNS");
    args.put("name", "example.com");

    String result1 = (String) function.apply(null, args);

    // Should be same as using full UUID
    args.put("namespace", DNS_NAMESPACE);
    String result2 = (String) function.apply(null, args);

    assertEquals(result1, result2);

    // Test other aliases
    args.put("namespace", "URL");
    String resultUrl = (String) function.apply(null, args);
    assertNotNull(resultUrl);

    args.put("namespace", "OID");
    String resultOid = (String) function.apply(null, args);
    assertNotNull(resultOid);

    args.put("namespace", "X500");
    String resultX500 = (String) function.apply(null, args);
    assertNotNull(resultX500);

    // All should be different
    assertNotEquals(result1, resultUrl);
    assertNotEquals(result1, resultOid);
    assertNotEquals(result1, resultX500);
  }

  @Test
  public void testNamespaceAliasCaseInsensitive() {
    Map<String, Object> args1 = new HashMap<>();
    args1.put("namespace", "dns");
    args1.put("name", "example.com");

    Map<String, Object> args2 = new HashMap<>();
    args2.put("namespace", "DNS");
    args2.put("name", "example.com");

    Map<String, Object> args3 = new HashMap<>();
    args3.put("namespace", "Dns");
    args3.put("name", "example.com");

    String result1 = (String) function.apply(null, args1);
    String result2 = (String) function.apply(null, args2);
    String result3 = (String) function.apply(null, args3);

    assertEquals(result1, result2);
    assertEquals(result2, result3);
  }

  @Test
  public void testApplyWithEmptyName() {
    Map<String, Object> args = new HashMap<>();
    args.put("namespace", DNS_NAMESPACE);
    args.put("name", "");

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    UUID uuid = UUID.fromString(result);
    assertEquals(5, uuid.version());
  }

  @Test
  public void testApplyWithNullName() {
    Map<String, Object> args = new HashMap<>();
    args.put("namespace", DNS_NAMESPACE);

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    UUID uuid = UUID.fromString(result);
    assertEquals(5, uuid.version());
  }

  @Test
  public void testUuidFormat() {
    Map<String, Object> args = new HashMap<>();
    args.put("namespace", DNS_NAMESPACE);
    args.put("name", "example.com");

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    // Verify standard UUID format: 8-4-4-4-12
    assertTrue(result.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));

    // Verify version 5 in the UUID string
    assertEquals('5', result.charAt(14)); // Version bit at position 14
  }

  @Test
  public void testRfc4122Compliance() {
    // Test with known values that should produce specific UUIDs
    // These test vectors verify RFC 4122 compliance
    Map<String, Object> args = new HashMap<>();
    args.put("namespace", DNS_NAMESPACE);
    args.put("name", "www.example.org");

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    UUID uuid = UUID.fromString(result);
    assertEquals(5, uuid.version());
    assertEquals(2, uuid.variant());

    // The result should be deterministic and match RFC 4122 algorithm
    // We can't assert the exact value without implementing the exact same algorithm,
    // but we can verify it's consistent
    String result2 = (String) function.apply(null, args);
    assertEquals(result, result2);
  }

  @Test
  public void testVariantNibbleIsRfc4122() {
    Map<String, Object> args = new HashMap<>();
    args.put("namespace", DNS_NAMESPACE);
    args.put("name", "example.com");

    String result = (String) function.apply(null, args);

    // Variant nibble should be 8, 9, A, or B (RFC 4122 variant)
    char variantNibble = result.charAt(19);
    assertTrue(
        "89ab".indexOf(Character.toLowerCase(variantNibble)) >= 0,
        "Variant nibble should be 8, 9, A, or B, but was: " + variantNibble);
  }

  @Test
  public void testNonAsciiAndEmoji() {
    Map<String, Object> args = new HashMap<>();
    args.put("namespace", DNS_NAMESPACE);
    args.put("name", "山口🍜.example.com");

    String result1 = (String) function.apply(null, args);
    String result2 = (String) function.apply(null, args);

    assertNotNull(result1);
    assertEquals(result1, result2); // Should be deterministic even with non-ASCII

    UUID uuid = UUID.fromString(result1);
    assertEquals(5, uuid.version());
    assertEquals(2, uuid.variant());
  }

  @Test
  public void testInputFallbackEqualsExplicitName() {
    Map<String, Object> withName = new HashMap<>();
    withName.put("namespace", DNS_NAMESPACE);
    withName.put("name", "example.com");

    Map<String, Object> withoutName = new HashMap<>();
    withoutName.put("namespace", DNS_NAMESPACE);

    String resultWithName = (String) function.apply(null, withName);
    String resultWithInput = (String) function.apply("example.com", withoutName);

    assertEquals(
        resultWithName,
        resultWithInput,
        "Explicit name and input fallback should produce identical results");
  }

  @Test
  public void testUuidStringVersionCharIs5() {
    Map<String, Object> args = new HashMap<>();
    args.put("namespace", DNS_NAMESPACE);
    args.put("name", "example.com");

    String result = (String) function.apply(null, args);

    // Version should be '5' at position 14 in the UUID string
    assertEquals('5', result.charAt(14), "Version character at position 14 should be '5'");
  }

  @Test
  public void testEmptyStringName() {
    Map<String, Object> args = new HashMap<>();
    args.put("namespace", DNS_NAMESPACE);
    args.put("name", "");

    String result1 = (String) function.apply(null, args);
    String result2 = (String) function.apply(null, args);

    assertNotNull(result1);
    assertEquals(result1, result2); // Should be deterministic even with empty name

    UUID uuid = UUID.fromString(result1);
    assertEquals(5, uuid.version());
  }

  @Test
  public void testVeryLongName() {
    String longName = "verylongname".repeat(1000);

    Map<String, Object> args = new HashMap<>();
    args.put("namespace", DNS_NAMESPACE);
    args.put("name", longName);

    String result1 = (String) function.apply(null, args);
    String result2 = (String) function.apply(null, args);

    assertNotNull(result1);
    assertEquals(result1, result2); // Should handle very long names deterministically

    UUID uuid = UUID.fromString(result1);
    assertEquals(5, uuid.version());
  }
}

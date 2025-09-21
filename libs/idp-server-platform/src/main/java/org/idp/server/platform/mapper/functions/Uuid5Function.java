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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code Uuid5Function} generates a deterministic UUID version 5 based on a namespace and name
 * using SHA-1 hashing.
 *
 * <p>UUID v5 is deterministic - the same namespace and name will always produce the same UUID. This
 * is useful for generating consistent identifiers based on known input values.
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>namespace</b>: UUID namespace (required). Can be a standard namespace UUID or custom
 *       UUID.
 *   <li><b>name</b>: Name to hash within the namespace (optional, uses input value if not
 *       specified).
 * </ul>
 *
 * <p>Standard namespaces:
 *
 * <ul>
 *   <li>DNS: {@code "6ba7b810-9dad-11d1-80b4-00c04fd430c8"}
 *   <li>URL: {@code "6ba7b811-9dad-11d1-80b4-00c04fd430c8"}
 *   <li>OID: {@code "6ba7b812-9dad-11d1-80b4-00c04fd430c8"}
 *   <li>X.500 DN: {@code "6ba7b814-9dad-11d1-80b4-00c04fd430c8"}
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * {
 *   "name": "uuid5",
 *   "args": {
 *     "namespace": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
 *     "name": "example.com"
 *   }
 * }
 * }</pre>
 *
 * @see <a href="https://tools.ietf.org/html/rfc4122#section-4.3">RFC 4122 Section 4.3</a>
 */
public class Uuid5Function implements ValueFunction {

  // Standard namespaces from RFC 4122
  private static final Map<String, UUID> STANDARD_NAMESPACES =
      Map.of(
          "DNS", UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8"),
          "URL", UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8"),
          "OID", UUID.fromString("6ba7b812-9dad-11d1-80b4-00c04fd430c8"),
          "X500", UUID.fromString("6ba7b814-9dad-11d1-80b4-00c04fd430c8"));

  /**
   * Generates a UUID version 5 based on namespace and name.
   *
   * @param input the input value (used as name if 'name' argument is not provided)
   * @param args function arguments containing 'namespace' and optionally 'name'
   * @return a deterministic UUID v5 as a string
   * @throws IllegalArgumentException if namespace is missing or invalid
   */
  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (args == null) {
      throw new IllegalArgumentException("uuid5: args is null");
    }

    String namespaceStr = (String) args.get("namespace");
    if (namespaceStr == null || namespaceStr.isEmpty()) {
      throw new IllegalArgumentException("uuid5: 'namespace' is required");
    }

    // Use explicit name argument if provided, otherwise use input value (can be empty string)
    String name = (String) args.get("name");
    if (name == null) {
      name = input != null ? input.toString() : "";
    }

    try {
      UUID namespace = parseNamespace(namespaceStr);
      return generateUuid5(namespace, name).toString();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("uuid5: Invalid namespace '" + namespaceStr + "'", e);
    }
  }

  @Override
  public String name() {
    return "uuid5";
  }

  /**
   * Parses namespace string, supporting both standard aliases and UUID strings.
   *
   * @param namespaceStr namespace string (UUID or alias like "DNS", "URL", etc.)
   * @return parsed UUID namespace
   * @throws IllegalArgumentException if namespace is invalid
   */
  private UUID parseNamespace(String namespaceStr) {
    String key = namespaceStr.trim().toUpperCase(Locale.ROOT);
    if (STANDARD_NAMESPACES.containsKey(key)) {
      return STANDARD_NAMESPACES.get(key);
    }
    return UUID.fromString(namespaceStr); // Regular UUID string
  }

  /**
   * Generates a UUID version 5 using SHA-1 hash algorithm.
   *
   * <p>This implementation follows RFC 4122 precisely by setting version and variant bits at the
   * correct byte positions in the hash result.
   *
   * @param namespace the namespace UUID
   * @param name the name to hash (can be empty string)
   * @return generated UUID v5
   */
  private UUID generateUuid5(UUID namespace, String name) {
    try {
      MessageDigest sha1 = MessageDigest.getInstance("SHA-1");

      // Add namespace bytes in network byte order (big-endian)
      sha1.update(toBytes(namespace));

      // Add name bytes in UTF-8 encoding
      sha1.update(name.getBytes(StandardCharsets.UTF_8));

      byte[] hash = sha1.digest(); // 20 bytes

      // RFC 4122: Set version and variant bits at correct byte positions
      hash[6] &= 0x0F; // Clear upper 4 bits of time_hi_and_version
      hash[6] |= 0x50; // Set version 5 (0101) in upper 4 bits

      hash[8] &= 0x3F; // Clear upper 2 bits of clock_seq_hi_and_reserved
      hash[8] |= 0x80; // Set variant RFC 4122 (10) in upper 2 bits

      // Convert first 16 bytes to UUID using big-endian byte order
      long msb = 0;
      long lsb = 0;
      for (int i = 0; i < 8; i++) {
        msb = (msb << 8) | (hash[i] & 0xFFL);
      }
      for (int i = 8; i < 16; i++) {
        lsb = (lsb << 8) | (hash[i] & 0xFFL);
      }

      return new UUID(msb, lsb);

    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-1 algorithm not available", e);
    }
  }

  /**
   * Converts UUID to byte array in network byte order (big-endian).
   *
   * @param uuid UUID to convert
   * @return 16-byte array representing the UUID
   */
  private static byte[] toBytes(UUID uuid) {
    byte[] out = new byte[16];
    long msb = uuid.getMostSignificantBits();
    long lsb = uuid.getLeastSignificantBits();

    // Convert most significant bits (bytes 0-7)
    for (int i = 7; i >= 0; i--) {
      out[i] = (byte) (msb & 0xFF);
      msb >>>= 8;
    }

    // Convert least significant bits (bytes 8-15)
    for (int i = 15; i >= 8; i--) {
      out[i] = (byte) (lsb & 0xFF);
      lsb >>>= 8;
    }

    return out;
  }
}

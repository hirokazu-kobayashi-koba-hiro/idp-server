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

package org.idp.server.platform.jose;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonWebSignatureFactoryP8Test {

  private JsonWebSignatureFactory factory;

  // Sample APNs P8-like private key (EC private key in PEM format)
  // This is a test key - not a real APNs key
  private static final String TEST_P8_PRIVATE_KEY =
      """
      -----BEGIN PRIVATE KEY-----
      MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg8RjbKiOfeCEhJF+V
      H6A8q4lZKdWNWjC8h4GNPpCJ8kOhRANCAATJKK4ZfgzJJp6YQm6OJ3EiGSgvK9EG
      z4ZbUjq5N6xtBz9UZV7m8eO8GG9z7o4JW9a1x3z8k8z4o2j7F1jz8o2k
      -----END PRIVATE KEY-----
      """;

  @BeforeEach
  void setUp() {
    factory = new JsonWebSignatureFactory();
  }

  @Test
  void createWithAsymmetricKeyForPem_shouldHandleP8PrivateKey() throws Exception {
    // Test that P8 private key (EC private key only) can be used to create JWS
    Map<String, Object> claims = createTestClaims();
    Map<String, Object> customHeaders = new HashMap<>();
    customHeaders.put("kid", "test-key-id");

    // This should not throw an exception even though P8 key has no public key info
    assertDoesNotThrow(
        () -> {
          try {
            JsonWebSignature jws =
                factory.createWithAsymmetricKeyForPem(claims, customHeaders, TEST_P8_PRIVATE_KEY);

            // Verify that JWS was created successfully
            assertNotNull(jws);
            assertNotNull(jws.serialize());
            assertTrue(jws.serialize().split("\\.").length == 3); // JWT has 3 parts

            // Verify the header contains the expected algorithm
            String[] parts = jws.serialize().split("\\.");
            assertEquals(3, parts.length); // JWT has 3 parts

            // Decode header to check algorithm
            String headerJson = new String(java.util.Base64.getUrlDecoder().decode(parts[0]));
            assertTrue(
                headerJson.contains("ES256"), "Expected ES256 algorithm in header: " + headerJson);

          } catch (Exception e) {
            // If we get here, log the error for debugging
            fail("Expected P8 key to be handled successfully, but got: " + e.getMessage());
          }
        });
  }

  @Test
  void createWithAsymmetricKeyForPem_stringClaims_shouldHandleP8PrivateKey() throws Exception {
    String claims = createTestClaimsAsString();
    Map<String, Object> customHeaders = new HashMap<>();
    customHeaders.put("kid", "test-key-id");

    // Test with string claims version
    assertDoesNotThrow(
        () -> {
          try {
            JsonWebSignature jws =
                factory.createWithAsymmetricKeyForPem(claims, customHeaders, TEST_P8_PRIVATE_KEY);

            assertNotNull(jws);
            assertNotNull(jws.serialize());
            assertTrue(jws.serialize().split("\\.").length == 3);

          } catch (Exception e) {
            fail(
                "Expected P8 key to be handled successfully with string claims, but got: "
                    + e.getMessage());
          }
        });
  }

  private Map<String, Object> createTestClaims() {
    Map<String, Object> claims = new HashMap<>();
    claims.put("iss", "test-team-id");
    claims.put("iat", System.currentTimeMillis() / 1000);
    return claims;
  }

  private String createTestClaimsAsString() {
    return """
        {
          "iss": "test-team-id",
          "iat": %d
        }
        """
        .formatted(System.currentTimeMillis() / 1000);
  }
}

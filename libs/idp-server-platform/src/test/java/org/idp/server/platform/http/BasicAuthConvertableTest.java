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

package org.idp.server.platform.http;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BasicAuthConvertableTest {

  private BasicAuthConvertable converter;

  @BeforeEach
  void setUp() {
    converter = new BasicAuthConvertable() {};
  }

  private String encode(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes());
  }

  @Nested
  @DisplayName("valid Authorization header")
  class ValidHeader {

    @Test
    @DisplayName("should parse username and password")
    void shouldParseUsernameAndPassword() {
      String header = "Basic " + encode("user:password");

      BasicAuth result = converter.convertBasicAuth(header);

      assertTrue(result.exists());
      assertEquals("user", result.username());
      assertEquals("password", result.password());
    }

    @Test
    @DisplayName("should handle password containing colons")
    void shouldHandlePasswordContainingColons() {
      String header = "Basic " + encode("user:pass:word:123");

      BasicAuth result = converter.convertBasicAuth(header);

      assertTrue(result.exists());
      assertEquals("user", result.username());
      assertEquals("pass:word:123", result.password());
    }

    @Test
    @DisplayName("should handle special characters in credentials")
    void shouldHandleSpecialCharacters() {
      String header = "Basic " + encode("user@example.com:p@$$w0rd!#%");

      BasicAuth result = converter.convertBasicAuth(header);

      assertTrue(result.exists());
      assertEquals("user@example.com", result.username());
      assertEquals("p@$$w0rd!#%", result.password());
    }

    @Test
    @DisplayName("should use standard Base64 decoder per RFC 7617")
    void shouldUseStandardBase64Decoder() {
      // '+' and '/' are standard Base64 characters that differ from URL-safe Base64
      String credentials = "user+name/test:pass+word/test";
      String header = "Basic " + encode(credentials);

      BasicAuth result = converter.convertBasicAuth(header);

      assertTrue(result.exists());
      assertEquals("user+name/test", result.username());
      assertEquals("pass+word/test", result.password());
    }
  }

  @Nested
  @DisplayName("invalid Authorization header")
  class InvalidHeader {

    @Test
    @DisplayName("should return empty BasicAuth when header is null")
    void shouldReturnEmptyWhenNull() {
      BasicAuth result = converter.convertBasicAuth(null);

      assertFalse(result.exists());
    }

    @Test
    @DisplayName("should return empty BasicAuth when header is empty")
    void shouldReturnEmptyWhenEmpty() {
      BasicAuth result = converter.convertBasicAuth("");

      assertFalse(result.exists());
    }

    @Test
    @DisplayName("should return empty BasicAuth when header is not Basic scheme")
    void shouldReturnEmptyWhenNotBasicScheme() {
      BasicAuth result = converter.convertBasicAuth("Bearer some-token");

      assertFalse(result.exists());
    }

    @Test
    @DisplayName("should return empty BasicAuth when decoded value has no colon")
    void shouldReturnEmptyWhenNoColon() {
      String header = "Basic " + encode("usernameonly");

      BasicAuth result = converter.convertBasicAuth(header);

      assertFalse(result.exists());
    }
  }

  @Nested
  @DisplayName("edge cases")
  class EdgeCases {

    @Test
    @DisplayName("should handle empty username with password")
    void shouldHandleEmptyUsername() {
      String header = "Basic " + encode(":password");

      BasicAuth result = converter.convertBasicAuth(header);

      assertEquals("", result.username());
      assertEquals("password", result.password());
    }

    @Test
    @DisplayName("should handle username with empty password")
    void shouldHandleEmptyPassword() {
      String header = "Basic " + encode("user:");

      BasicAuth result = converter.convertBasicAuth(header);

      assertEquals("user", result.username());
      assertEquals("", result.password());
    }

    @Test
    @DisplayName("should split at first colon per RFC 7617 (user-id MUST NOT contain colon)")
    void shouldSplitAtFirstColon() {
      String header = "Basic " + encode("us:er:pass");

      BasicAuth result = converter.convertBasicAuth(header);

      assertEquals("us", result.username());
      assertEquals("er:pass", result.password());
    }
  }
}

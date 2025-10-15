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

package org.idp.server.core.openid.token;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import org.idp.server.platform.http.BasicAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthorizationHeaderHandlerableTest {

  static class TestHandler implements AuthorizationHeaderHandlerable {}

  private final TestHandler handler = new TestHandler();

  @Test
  @DisplayName("convertBasicAuth should return valid BasicAuth for correct format")
  void testConvertBasicAuthWithValidFormat() {
    String credentials = Base64.getUrlEncoder().encodeToString("username:password".getBytes());
    String authHeader = "Basic " + credentials;

    BasicAuth result = handler.convertBasicAuth(authHeader);

    assertTrue(result.exists());
    assertEquals("username", result.username());
    assertEquals("password", result.password());
  }

  @Test
  @DisplayName("convertBasicAuth should handle password containing colon")
  void testConvertBasicAuthWithColonInPassword() {
    String credentials = Base64.getUrlEncoder().encodeToString("user:pass:word".getBytes());
    String authHeader = "Basic " + credentials;

    BasicAuth result = handler.convertBasicAuth(authHeader);

    assertTrue(result.exists());
    assertEquals("user", result.username());
    assertEquals("pass:word", result.password());
  }

  @Test
  @DisplayName("convertBasicAuth should return empty BasicAuth for missing colon")
  void testConvertBasicAuthWithMissingColon() {
    String credentials = Base64.getUrlEncoder().encodeToString("usernameonly".getBytes());
    String authHeader = "Basic " + credentials;

    BasicAuth result = handler.convertBasicAuth(authHeader);

    assertFalse(result.exists());
  }

  @Test
  @DisplayName("convertBasicAuth should return empty BasicAuth for trailing colon")
  void testConvertBasicAuthWithTrailingColon() {
    String credentials = Base64.getUrlEncoder().encodeToString("username:".getBytes());
    String authHeader = "Basic " + credentials;

    BasicAuth result = handler.convertBasicAuth(authHeader);

    assertFalse(result.exists());
  }

  @Test
  @DisplayName("convertBasicAuth should return empty BasicAuth for invalid Base64")
  void testConvertBasicAuthWithInvalidBase64() {
    String authHeader = "Basic !!!invalid-base64!!!";

    BasicAuth result = handler.convertBasicAuth(authHeader);

    assertFalse(result.exists());
  }

  @Test
  @DisplayName("convertBasicAuth should return empty BasicAuth for non-Basic auth")
  void testConvertBasicAuthWithNonBasicAuth() {
    String authHeader = "Bearer some-token";

    BasicAuth result = handler.convertBasicAuth(authHeader);

    assertFalse(result.exists());
  }

  @Test
  @DisplayName("convertBasicAuth should return empty BasicAuth for null header")
  void testConvertBasicAuthWithNullHeader() {
    BasicAuth result = handler.convertBasicAuth(null);

    assertFalse(result.exists());
  }

  @Test
  @DisplayName("convertBasicAuth should return empty BasicAuth for empty header")
  void testConvertBasicAuthWithEmptyHeader() {
    BasicAuth result = handler.convertBasicAuth("");

    assertFalse(result.exists());
  }

  @Test
  @DisplayName("convertBasicAuth should handle empty username")
  void testConvertBasicAuthWithEmptyUsername() {
    String credentials = Base64.getUrlEncoder().encodeToString(":password".getBytes());
    String authHeader = "Basic " + credentials;

    BasicAuth result = handler.convertBasicAuth(authHeader);

    assertFalse(result.exists());
  }

  @Test
  @DisplayName("convertBasicAuth should handle empty password")
  void testConvertBasicAuthWithEmptyPassword() {
    String credentials = Base64.getUrlEncoder().encodeToString("username:".getBytes());
    String authHeader = "Basic " + credentials;

    BasicAuth result = handler.convertBasicAuth(authHeader);

    assertFalse(result.exists());
  }
}

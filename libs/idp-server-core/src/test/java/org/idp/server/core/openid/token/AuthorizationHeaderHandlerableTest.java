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
    String credentials = Base64.getEncoder().encodeToString("username:password".getBytes());
    String authHeader = "Basic " + credentials;

    BasicAuth result = handler.convertBasicAuth(authHeader);

    assertTrue(result.exists());
    assertEquals("username", result.username());
    assertEquals("password", result.password());
  }

  @Test
  @DisplayName("convertBasicAuth should decode standard Base64 containing + and /")
  void convertBasicAuthWithStandardBase64Alphabet() {
    // RFC 7617 uses standard Base64 (RFC 4648 Section 4). The URL-safe decoder rejects + and /,
    // which appear in roughly one of every 32 characters of a Base64-encoded secret, and the
    // resulting IllegalArgumentException is swallowed into an empty BasicAuth -- an authentication
    // failure with no diagnostic. Building the header with getUrlEncoder() hid this for the whole
    // suite, so the credential here is chosen to produce both characters.
    String credentials = Base64.getEncoder().encodeToString("s6BhdRkqt3:~~~?????".getBytes());
    assertTrue(credentials.contains("+") || credentials.contains("/"));

    BasicAuth result = handler.convertBasicAuth("Basic " + credentials);

    assertTrue(result.exists());
    assertEquals("s6BhdRkqt3", result.username());
    assertEquals("~~~?????", result.password());
  }

  @Test
  @DisplayName("convertBasicAuth should NOT apply RFC 6749 Appendix B decoding")
  void convertBasicAuthKeepsCredentialAsTransmitted() {
    // Section 2.3.1 governs OAuth client authentication only. Other Basic-authenticated surfaces
    // (the management API) must see the credential exactly as sent, or a secret containing + would
    // be rewritten into a space.
    String credentials = Base64.getEncoder().encodeToString("admin:a+b%2Bc".getBytes());

    BasicAuth result = handler.convertBasicAuth("Basic " + credentials);

    assertEquals("a+b%2Bc", result.password());
  }

  @Test
  @DisplayName("convertClientSecretBasicAuth should apply RFC 6749 Appendix B decoding")
  void convertClientSecretBasicAuthDecodesFormUrlEncoding() {
    // %3A keeps a colon inside either half from being mistaken for the separator, and per
    // Appendix B a literal plus sign arrives as %2B while + itself denotes a space.
    String credentials = Base64.getEncoder().encodeToString("cli%3Aent:pa%3Ass%2Bword".getBytes());

    BasicAuth result = handler.convertClientSecretBasicAuth("Basic " + credentials);

    assertEquals("cli:ent", result.username());
    assertEquals("pa:ss+word", result.password());
  }

  @Test
  @DisplayName("convertBasicAuth should handle password containing colon")
  void testConvertBasicAuthWithColonInPassword() {
    String credentials = Base64.getEncoder().encodeToString("user:pass:word".getBytes());
    String authHeader = "Basic " + credentials;

    BasicAuth result = handler.convertBasicAuth(authHeader);

    assertTrue(result.exists());
    assertEquals("user", result.username());
    assertEquals("pass:word", result.password());
  }

  @Test
  @DisplayName("convertBasicAuth should return empty BasicAuth for missing colon")
  void testConvertBasicAuthWithMissingColon() {
    String credentials = Base64.getEncoder().encodeToString("usernameonly".getBytes());
    String authHeader = "Basic " + credentials;

    BasicAuth result = handler.convertBasicAuth(authHeader);

    assertFalse(result.exists());
  }

  @Test
  @DisplayName("convertBasicAuth should return empty BasicAuth for trailing colon")
  void testConvertBasicAuthWithTrailingColon() {
    String credentials = Base64.getEncoder().encodeToString("username:".getBytes());
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
    String credentials = Base64.getEncoder().encodeToString(":password".getBytes());
    String authHeader = "Basic " + credentials;

    BasicAuth result = handler.convertBasicAuth(authHeader);

    assertFalse(result.exists());
  }

  @Test
  @DisplayName("convertBasicAuth should handle empty password")
  void testConvertBasicAuthWithEmptyPassword() {
    String credentials = Base64.getEncoder().encodeToString("username:".getBytes());
    String authHeader = "Basic " + credentials;

    BasicAuth result = handler.convertBasicAuth(authHeader);

    assertFalse(result.exists());
  }
}

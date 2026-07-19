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

package org.idp.server.adapters.springboot.application.restapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class SecurityHeaderConfigurableTest {

  /** Minimal implementor to exercise the interface's default methods. */
  private final SecurityHeaderConfigurable target = new SecurityHeaderConfigurable() {};

  @Test
  void escapeHeaderQuotedString_escapesQuoteAndBackslash() {
    assertEquals(
        "he said \\\"hi\\\"",
        SecurityHeaderConfigurable.escapeHeaderQuotedString("he said \"hi\""));
    assertEquals("a\\\\b", SecurityHeaderConfigurable.escapeHeaderQuotedString("a\\b"));
  }

  @Test
  void escapeHeaderQuotedString_stripsControlCharsToPreventHeaderInjection() {
    // CR/LF must not survive, otherwise a crafted description could inject a new header.
    String malicious = "bad\r\nSet-Cookie: pwned=1";
    String escaped = SecurityHeaderConfigurable.escapeHeaderQuotedString(malicious);
    assertFalse(escaped.contains("\r"));
    assertFalse(escaped.contains("\n"));
    assertEquals("badSet-Cookie: pwned=1", escaped);
  }

  @Test
  void escapeHeaderQuotedString_nullBecomesEmpty() {
    assertEquals("", SecurityHeaderConfigurable.escapeHeaderQuotedString(null));
  }

  @Test
  void applyWwwAuthenticate_setsDPoPSchemeWhenAuthorizationIsDpop() {
    HttpHeaders headers = new HttpHeaders();
    target.applyWwwAuthenticateIfUnauthorized(
        headers,
        401,
        Map.of("error", "invalid_token", "error_description", "token is expired"),
        "DPoP eyJ...");

    assertEquals(
        "DPoP error=\"invalid_token\", error_description=\"token is expired\"",
        headers.getFirst(HttpHeaders.WWW_AUTHENTICATE));
  }

  @Test
  void applyWwwAuthenticate_defaultsToBearerScheme() {
    HttpHeaders headers = new HttpHeaders();
    target.applyWwwAuthenticateIfUnauthorized(
        headers, 401, Map.of("error", "invalid_token", "error_description", "nope"), "Bearer abc");

    assertTrue(headers.getFirst(HttpHeaders.WWW_AUTHENTICATE).startsWith("Bearer error="));
  }

  @Test
  void applyWwwAuthenticate_escapesAttackerControlledDescription() {
    HttpHeaders headers = new HttpHeaders();
    target.applyWwwAuthenticateIfUnauthorized(
        headers,
        401,
        // htu-style value echoed into the description containing a quote + CRLF injection attempt.
        Map.of("error", "invalid_token", "error_description", "htu 'https://evil\"\r\nX: y' bad"),
        "DPoP proof");

    String header = headers.getFirst(HttpHeaders.WWW_AUTHENTICATE);
    assertFalse(header.contains("\r"));
    assertFalse(header.contains("\n"));
    // the inner quote is escaped, so the quoted-string is not broken
    assertTrue(header.contains("evil\\\""));
  }

  @Test
  void applyWwwAuthenticate_noHeaderWhenNotUnauthorized() {
    HttpHeaders headers = new HttpHeaders();
    target.applyWwwAuthenticateIfUnauthorized(headers, 200, Map.of(), "DPoP x");
    assertNull(headers.getFirst(HttpHeaders.WWW_AUTHENTICATE));
  }

  @Test
  void applyWwwAuthenticate_missingErrorFieldsFallBackToInvalidToken() {
    HttpHeaders headers = new HttpHeaders();
    target.applyWwwAuthenticateIfUnauthorized(headers, 401, Map.of(), null);
    assertEquals(
        "Bearer error=\"invalid_token\", error_description=\"\"",
        headers.getFirst(HttpHeaders.WWW_AUTHENTICATE));
  }
}

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

import org.idp.server.platform.exception.MaliciousInputException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UrlParameterSanitizerTest {

  @Nested
  class SanitizePath {

    @Test
    void returnsEncodedValue() {
      String result = UrlParameterSanitizer.sanitizePath("hello world");
      assertEquals("hello+world", result);
    }

    @Test
    void handlesEmptyString() {
      assertEquals("", UrlParameterSanitizer.sanitizePath(""));
    }

    @Test
    void handlesNull() {
      assertEquals("", UrlParameterSanitizer.sanitizePath(null));
    }

    @Test
    void rejectsPathTraversal() {
      MaliciousInputException ex =
          assertThrows(
              MaliciousInputException.class,
              () -> UrlParameterSanitizer.sanitizePath("../etc/passwd"));
      assertEquals("path_traversal", ex.attackType());
    }

    @Test
    void rejectsPathTraversalWithBackslash() {
      MaliciousInputException ex =
          assertThrows(
              MaliciousInputException.class,
              () -> UrlParameterSanitizer.sanitizePath("..\\windows\\system32"));
      assertEquals("path_traversal", ex.attackType());
    }

    @Test
    void rejectsUrlSchemeHttp() {
      MaliciousInputException ex =
          assertThrows(
              MaliciousInputException.class,
              () -> UrlParameterSanitizer.sanitizePath("http://evil.com"));
      assertEquals("url_scheme_injection", ex.attackType());
    }

    @Test
    void rejectsUrlSchemeHttps() {
      MaliciousInputException ex =
          assertThrows(
              MaliciousInputException.class,
              () -> UrlParameterSanitizer.sanitizePath("https://evil.com"));
      assertEquals("url_scheme_injection", ex.attackType());
    }

    @Test
    void rejectsUrlSchemeJavascript() {
      MaliciousInputException ex =
          assertThrows(
              MaliciousInputException.class,
              () -> UrlParameterSanitizer.sanitizePath("javascript:alert(1)"));
      assertEquals("url_scheme_injection", ex.attackType());
    }

    @Test
    void rejectsCrlfInjection() {
      MaliciousInputException ex =
          assertThrows(
              MaliciousInputException.class,
              () -> UrlParameterSanitizer.sanitizePath("value\r\nX-Injected: header"));
      assertEquals("crlf_injection", ex.attackType());
    }

    @Test
    void rejectsUnsafeCharacters() {
      MaliciousInputException ex1 =
          assertThrows(
              MaliciousInputException.class,
              () -> UrlParameterSanitizer.sanitizePath("<script>alert(1)</script>"));
      assertEquals("unsafe_characters", ex1.attackType());

      MaliciousInputException ex2 =
          assertThrows(
              MaliciousInputException.class, () -> UrlParameterSanitizer.sanitizePath("test|cat"));
      assertEquals("unsafe_characters", ex2.attackType());

      MaliciousInputException ex3 =
          assertThrows(
              MaliciousInputException.class, () -> UrlParameterSanitizer.sanitizePath("test;rm"));
      assertEquals("unsafe_characters", ex3.attackType());

      MaliciousInputException ex4 =
          assertThrows(
              MaliciousInputException.class, () -> UrlParameterSanitizer.sanitizePath("$HOME"));
      assertEquals("unsafe_characters", ex4.attackType());
    }

    @Test
    void allowsValidCharacters() {
      assertEquals("user123", UrlParameterSanitizer.sanitizePath("user123"));
      assertEquals("my-file_name", UrlParameterSanitizer.sanitizePath("my-file_name"));
      assertEquals("file.txt", UrlParameterSanitizer.sanitizePath("file.txt"));
    }
  }

  @Nested
  class EncodeQueryKey {

    @Test
    void encodesSpaces() {
      assertEquals("key+name", UrlParameterSanitizer.encodeQueryKey("key name"));
    }

    @Test
    void encodesSpecialCharacters() {
      assertEquals("key%3D", UrlParameterSanitizer.encodeQueryKey("key="));
      assertEquals("key%26", UrlParameterSanitizer.encodeQueryKey("key&"));
    }
  }

  @Nested
  class EncodeQueryValue {

    @Test
    void encodesSpaces() {
      assertEquals("hello+world", UrlParameterSanitizer.encodeQueryValue("hello world"));
    }

    @Test
    void encodesUrl() {
      assertEquals(
          "https%3A%2F%2Fexample.com",
          UrlParameterSanitizer.encodeQueryValue("https://example.com"));
    }

    @Test
    void preventsParameterInjection() {
      String result = UrlParameterSanitizer.encodeQueryValue("value&injected=true");
      assertEquals("value%26injected%3Dtrue", result);
    }
  }

  @Nested
  class SanitizeQuery {

    @Test
    void combinesKeyAndValue() {
      String result = UrlParameterSanitizer.sanitizeQuery("key", "value");
      assertEquals("key=value", result);
    }

    @Test
    void encodesKeyAndValue() {
      String result = UrlParameterSanitizer.sanitizeQuery("key name", "hello world");
      assertEquals("key+name=hello+world", result);
    }
  }
}

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

import java.util.Map;
import org.idp.server.platform.exception.MaliciousInputException;
import org.junit.jupiter.api.Test;

class HttpRequestUrlTest {

  @Test
  void interpolate_withValidParameter() {
    HttpRequestUrl url = new HttpRequestUrl("https://example.com/users/{{userId}}/profile");
    HttpRequestUrl result = url.interpolate(Map.of("userId", "12345"));
    assertEquals("https://example.com/users/12345/profile", result.value());
  }

  @Test
  void interpolate_withMultipleParameters() {
    HttpRequestUrl url = new HttpRequestUrl("https://example.com/{{tenantId}}/users/{{userId}}");
    HttpRequestUrl result = url.interpolate(Map.of("tenantId", "tenant1", "userId", "user1"));
    assertEquals("https://example.com/tenant1/users/user1", result.value());
  }

  @Test
  void interpolate_rejectsPathTraversal() {
    HttpRequestUrl url = new HttpRequestUrl("https://example.com/files/{{path}}");
    assertThrows(
        MaliciousInputException.class, () -> url.interpolate(Map.of("path", "../etc/passwd")));
  }

  @Test
  void interpolate_rejectsPathTraversalWithBackslash() {
    HttpRequestUrl url = new HttpRequestUrl("https://example.com/files/{{path}}");
    assertThrows(
        MaliciousInputException.class,
        () -> url.interpolate(Map.of("path", "..\\windows\\system32")));
  }

  @Test
  void interpolate_rejectsUrlSchemeInjection() {
    HttpRequestUrl url = new HttpRequestUrl("https://example.com/redirect/{{target}}");
    assertThrows(
        MaliciousInputException.class, () -> url.interpolate(Map.of("target", "http://evil.com")));
  }

  @Test
  void interpolate_rejectsHttpsSchemeInjection() {
    HttpRequestUrl url = new HttpRequestUrl("https://example.com/redirect/{{target}}");
    assertThrows(
        MaliciousInputException.class, () -> url.interpolate(Map.of("target", "https://evil.com")));
  }

  @Test
  void interpolate_rejectsCrlfInjection() {
    HttpRequestUrl url = new HttpRequestUrl("https://example.com/users/{{userId}}");
    assertThrows(
        MaliciousInputException.class,
        () -> url.interpolate(Map.of("userId", "user\r\nX-Injected: header")));
  }

  @Test
  void interpolate_rejectsUnsafeCharacters() {
    HttpRequestUrl url = new HttpRequestUrl("https://example.com/search/{{query}}");

    assertThrows(
        MaliciousInputException.class,
        () -> url.interpolate(Map.of("query", "<script>alert(1)</script>")));

    assertThrows(
        MaliciousInputException.class, () -> url.interpolate(Map.of("query", "test|cat /etc")));

    assertThrows(
        MaliciousInputException.class, () -> url.interpolate(Map.of("query", "test;rm -rf")));

    assertThrows(
        MaliciousInputException.class, () -> url.interpolate(Map.of("query", "test$HOME")));
  }

  @Test
  void interpolate_urlEncodesSpecialCharacters() {
    HttpRequestUrl url = new HttpRequestUrl("https://example.com/search/{{query}}");
    HttpRequestUrl result = url.interpolate(Map.of("query", "hello world"));
    assertEquals("https://example.com/search/hello+world", result.value());
  }

  @Test
  void interpolate_handlesEmptyValue() {
    HttpRequestUrl url = new HttpRequestUrl("https://example.com/users/{{userId}}");
    HttpRequestUrl result = url.interpolate(Map.of("userId", ""));
    assertEquals("https://example.com/users/", result.value());
  }

  @Test
  void withQueryParams_appendsToUrlWithoutQueryString() {
    HttpRequestUrl url = new HttpRequestUrl("https://example.com/api");
    HttpQueryParams params = new HttpQueryParams();
    params.add("key", "value");
    String result = url.withQueryParams(params);
    assertEquals("https://example.com/api?key=value", result);
  }

  @Test
  void withQueryParams_appendsToUrlWithExistingQueryString() {
    HttpRequestUrl url = new HttpRequestUrl("https://example.com/api?existing=param");
    HttpQueryParams params = new HttpQueryParams();
    params.add("key", "value");
    String result = url.withQueryParams(params);
    assertEquals("https://example.com/api?existing=param&key=value", result);
  }

  @Test
  void withQueryParams_handlesEmptyParams() {
    HttpRequestUrl url = new HttpRequestUrl("https://example.com/api");
    HttpQueryParams params = new HttpQueryParams();
    String result = url.withQueryParams(params);
    assertEquals("https://example.com/api", result);
  }
}

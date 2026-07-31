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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpRequestInputsTest {

  @Test
  @DisplayName("header keys are normalized to lower case and looked up case-insensitively")
  void headerLookupIsCaseInsensitive() {
    HttpRequestInputs inputs =
        new HttpRequestInputs(
            null,
            Map.of(),
            Map.of("DPoP", List.of("proof-jwt"), "X-Custom", List.of("a", "b")),
            null,
            "POST",
            "https://server.example.com/token");

    assertEquals(List.of("proof-jwt"), inputs.headerValues("dpop"));
    assertEquals(List.of("proof-jwt"), inputs.headerValues("DPOP"));
    assertEquals("proof-jwt", inputs.firstHeader("dPoP").orElseThrow());
    assertEquals(List.of("a", "b"), inputs.headerValues("x-custom"));
    assertTrue(inputs.hasHeader("X-CUSTOM"));
  }

  @Test
  @DisplayName("absent headers yield empty list / empty optional, never null")
  void absentHeaderIsEmpty() {
    HttpRequestInputs inputs =
        new HttpRequestInputs(null, Map.of(), Map.of(), null, "POST", "https://example.com");

    assertEquals(List.of(), inputs.headerValues("dpop"));
    assertTrue(inputs.firstHeader("dpop").isEmpty());
    assertFalse(inputs.hasHeader("dpop"));
  }

  @Test
  @DisplayName("null bodyParameters / headers are normalized to empty maps")
  void nullCollectionsAreNormalized() {
    HttpRequestInputs inputs = new HttpRequestInputs(null, null, null, null, "GET", "");

    assertNotNull(inputs.bodyParameters());
    assertTrue(inputs.bodyParameters().isEmpty());
    assertNotNull(inputs.headers());
    assertTrue(inputs.headers().isEmpty());
  }

  @Test
  @DisplayName("repeated header values are preserved for multiple-header violation detection")
  void repeatedHeaderValuesArePreserved() {
    HttpRequestInputs inputs =
        new HttpRequestInputs(
            null,
            Map.of(),
            Map.of("DPoP", List.of("proof-1", "proof-2")),
            null,
            "POST",
            "https://server.example.com/token");

    assertEquals(2, inputs.headerValues("dpop").size());
  }

  @Test
  @DisplayName("mixed-casing keys are merged, not silently overwritten")
  void mixedCasingKeysAreMerged() {
    HttpRequestInputs inputs =
        new HttpRequestInputs(
            null,
            Map.of(),
            Map.of("DPoP", List.of("proof-1"), "dpop", List.of("proof-2")),
            null,
            "POST",
            "https://server.example.com/token");

    assertEquals(2, inputs.headerValues("dpop").size());
  }

  @Test
  @DisplayName("toString masks credential-bearing values, exposing only names")
  void toStringMasksCredentials() {
    HttpRequestInputs inputs =
        new HttpRequestInputs(
            "Bearer secret-access-token",
            Map.of("client_secret", new String[] {"secret-client-secret"}),
            Map.of("Cookie", List.of("SESSION=secret-session"), "DPoP", List.of("secret-proof")),
            "-----BEGIN CERTIFICATE-----secret-cert",
            "POST",
            "https://server.example.com/token");

    String value = inputs.toString();

    assertFalse(value.contains("secret-access-token"));
    assertFalse(value.contains("secret-client-secret"));
    assertFalse(value.contains("secret-session"));
    assertFalse(value.contains("secret-proof"));
    assertFalse(value.contains("secret-cert"));
    assertTrue(value.contains("client_secret"));
    assertTrue(value.contains("cookie"));
    assertTrue(value.contains("https://server.example.com/token"));
  }
}

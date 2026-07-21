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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ParameterTransformableTest {

  /** Minimal implementor to exercise the interface's default resolveRequestUrl. */
  private final ParameterTransformable target = new ParameterTransformable() {};

  private MockHttpServletRequest requestWith(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    request.setScheme("http");
    request.setServerName("idp-server.internal");
    request.setServerPort(8080);
    request.setRequestURI(uri);
    return request;
  }

  @Test
  void usesForwardedProtoAndHostToReconstructClientFacingUrl() {
    MockHttpServletRequest request = requestWith("/tenant-x/v1/userinfo");
    request.addHeader("X-Forwarded-Proto", "https");
    request.addHeader("X-Forwarded-Host", "api.local.test");

    assertEquals("https://api.local.test/tenant-x/v1/userinfo", target.resolveRequestUrl(request));
  }

  @Test
  void usesLeftmostValueOfCommaSeparatedForwardedHeaders() {
    // A proxy chain (e.g. CloudFront/API Gateway -> ALB) accumulates the values; the leftmost is
    // the client-facing one. The raw comma value would corrupt the URL and break DPoP htu.
    MockHttpServletRequest request = requestWith("/tenant-x/v1/tokens");
    request.addHeader("X-Forwarded-Proto", "https, http");
    request.addHeader("X-Forwarded-Host", "api.local.test, idp-server.internal:8080");

    assertEquals("https://api.local.test/tenant-x/v1/tokens", target.resolveRequestUrl(request));
  }

  @Test
  void trimsWhitespaceAroundLeftmostForwardedValue() {
    MockHttpServletRequest request = requestWith("/tenant-x/v1/tokens");
    request.addHeader("X-Forwarded-Proto", "https , http");
    request.addHeader("X-Forwarded-Host", " api.local.test , inner");

    assertEquals("https://api.local.test/tenant-x/v1/tokens", target.resolveRequestUrl(request));
  }

  @Test
  void fallsBackToServerNameWhenForwardedHostAbsent() {
    MockHttpServletRequest request = requestWith("/tenant-x/v1/userinfo");
    request.addHeader("X-Forwarded-Proto", "https");

    assertEquals(
        "https://idp-server.internal/tenant-x/v1/userinfo", target.resolveRequestUrl(request));
  }

  @Test
  void fallsBackToRequestUrlWhenNoForwardedProto() {
    MockHttpServletRequest request = requestWith("/tenant-x/v1/userinfo");

    assertEquals(
        "http://idp-server.internal:8080/tenant-x/v1/userinfo", target.resolveRequestUrl(request));
  }
}

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

package org.idp.server.adapters.springboot;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class NullByteRejectFilterTest {

  private final NullByteRejectFilter filter = new NullByteRejectFilter();

  @Test
  void normalRequest_passesThrough() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test");
    request.setContentType("application/x-www-form-urlencoded");
    request.addParameter("scope", "openid profile");
    request.addParameter("client_id", "test-client");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    filter.doFilterInternal(request, response, filterChain);

    assertEquals(200, response.getStatus());
    assertNotNull(filterChain.getRequest());
  }

  @Test
  void parameterValueContainsNullByte_returns400() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test");
    request.setContentType("application/x-www-form-urlencoded");
    request.addParameter("binding_message", "hello\0world");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    filter.doFilterInternal(request, response, filterChain);

    assertEquals(400, response.getStatus());
    assertTrue(response.getContentAsString().contains("invalid_request"));
    assertTrue(response.getContentAsString().contains("Invalid parameter value"));
    assertNull(filterChain.getRequest());
  }

  @Test
  void parameterNameContainsNullByte_returns400() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test");
    request.setContentType("application/x-www-form-urlencoded");
    request.addParameter("bad\0param", "value");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    filter.doFilterInternal(request, response, filterChain);

    assertEquals(400, response.getStatus());
    assertNull(filterChain.getRequest());
  }

  @Test
  void nullByteAtStartOfValue_returns400() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test");
    request.setContentType("application/x-www-form-urlencoded");
    request.addParameter("scope", "\0openid");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    filter.doFilterInternal(request, response, filterChain);

    assertEquals(400, response.getStatus());
    assertNull(filterChain.getRequest());
  }

  @Test
  void nullByteAtEndOfValue_returns400() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test");
    request.setContentType("application/x-www-form-urlencoded");
    request.addParameter("login_hint", "user@example.com\0");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    filter.doFilterInternal(request, response, filterChain);

    assertEquals(400, response.getStatus());
    assertNull(filterChain.getRequest());
  }

  @Test
  void jsonBodyContainsNullByte_returns400() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test");
    request.setContentType("application/json");
    request.setContent("{\"name\":\"hello\0world\"}".getBytes());
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    filter.doFilterInternal(request, response, filterChain);

    assertEquals(400, response.getStatus());
    assertTrue(response.getContentAsString().contains("invalid_request"));
    assertNull(filterChain.getRequest());
  }

  @Test
  void jsonBodyWithoutNullByte_passesThrough() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test");
    request.setContentType("application/json");
    request.setContent("{\"name\":\"hello world\"}".getBytes());
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    filter.doFilterInternal(request, response, filterChain);

    assertEquals(200, response.getStatus());
    assertNotNull(filterChain.getRequest());
  }

  @Test
  void getRequestWithNullByteInQueryParam_returns400() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
    request.addParameter("search", "test\0value");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    filter.doFilterInternal(request, response, filterChain);

    assertEquals(400, response.getStatus());
    assertNull(filterChain.getRequest());
  }

  @Test
  void emptyPostBody_passesThrough() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test");
    request.setContentType("application/json");
    request.setContent(new byte[0]);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    filter.doFilterInternal(request, response, filterChain);

    assertEquals(200, response.getStatus());
    assertNotNull(filterChain.getRequest());
  }
}

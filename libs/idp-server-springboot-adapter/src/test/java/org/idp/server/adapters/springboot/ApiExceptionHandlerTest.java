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

import java.util.Map;
import org.idp.server.platform.oauth.OAuthAuthorizationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {

  /**
   * #1384: an external-service OAuth token failure must map to a meaningful 502
   * external_service_error (not a generic 500 server_error), and must not echo the upstream
   * token-endpoint body to the client.
   */
  @Test
  void oauthAuthorizationExceptionMapsTo502WithoutLeakingUpstreamBody() {
    ApiExceptionHandler handler = new ApiExceptionHandler();
    String upstreamBody = "{\"error\":\"invalid_client\",\"internal\":\"do-not-leak\"}";

    ResponseEntity<?> response =
        handler.handleException(new OAuthAuthorizationException(401, upstreamBody));

    assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());

    @SuppressWarnings("unchecked")
    Map<String, String> body = (Map<String, String>) response.getBody();
    assertEquals("external_service_error", body.get("error"));
    assertFalse(
        body.get("error_description").contains("do-not-leak"),
        "upstream token-endpoint body must not be echoed to the client");
  }
}

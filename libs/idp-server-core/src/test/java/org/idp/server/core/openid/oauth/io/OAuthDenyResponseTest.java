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
package org.idp.server.core.openid.oauth.io;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The deny endpoint's error body uses the same keys as every other error response (Issue #1919).
 */
class OAuthDenyResponseTest {

  @Test
  void errorBodyUsesSnakeCaseErrorDescription() {
    OAuthDenyResponse response =
        new OAuthDenyResponse(
            OAuthDenyStatus.BAD_REQUEST, "invalid_request", "client configuration not found");

    Map<String, Object> contents = response.contents();

    assertEquals("invalid_request", contents.get("error"));
    assertEquals("client configuration not found", contents.get("error_description"));
    assertFalse(contents.containsKey("errorDescription"));
  }
}

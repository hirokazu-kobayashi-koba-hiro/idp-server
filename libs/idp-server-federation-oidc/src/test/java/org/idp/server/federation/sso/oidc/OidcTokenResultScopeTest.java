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

package org.idp.server.federation.sso.oidc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OidcTokenResultScopeTest {

  static OidcTokenResult responding(Object scope) {
    Map<String, Object> body = new HashMap<>();
    body.put("access_token", "token");
    if (scope != null) {
      body.put("scope", scope);
    }
    return new OidcTokenResult(200, Map.of(), body);
  }

  @Test
  void takesTheScopeTheResponseGrants() {
    assertEquals("read:user", responding("read:user").scopeOr("read:user repo"));
  }

  /** RFC 6749 §5.1: a response without a scope grants the one requested. */
  @Test
  void takesTheFallbackWhenTheResponseNamesNoScope() {
    assertEquals("read:user", responding(null).scopeOr("read:user"));
    assertEquals("read:user", responding("").scopeOr("read:user"));
    assertEquals("read:user", responding(" ").scopeOr("read:user"));
    assertEquals("read:user", responding(List.of("repo")).scopeOr("read:user"));
  }
}

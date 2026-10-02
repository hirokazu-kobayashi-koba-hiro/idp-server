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

package org.idp.server.core.openid.authentication.interaction.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExternalRequestUserContextCreatorTest {

  private User registeredUser() {
    return new User()
        .setSub("user-1")
        .setProviderId("idp-server")
        .setEmail("user@example.com")
        .setStatus(UserStatus.REGISTERED);
  }

  @Test
  @DisplayName("external_user_id is exposed when the user has one (Issue #1930)")
  void externalUserIdExposed() {
    User user = registeredUser().setExternalUserId("ext-123");

    Map<String, Object> context = ExternalRequestUserContextCreator.create(user);

    assertEquals("ext-123", context.get("external_user_id"));
    assertEquals("idp-server", context.get("provider_id"));
  }

  @Test
  @DisplayName("external_user_id is absent, not null, when the user has none")
  void externalUserIdAbsentWithoutValue() {
    Map<String, Object> context = ExternalRequestUserContextCreator.create(registeredUser());

    assertFalse(context.containsKey("external_user_id"));
  }

  @Test
  @DisplayName("credentials, verified_claims and evaluation-only signals are never exposed")
  void sensitiveExcluded() {
    User user =
        registeredUser()
            .setExternalUserId("ext-123")
            .setHashedPassword("$2a$10$abcdefghijklmnopqrstuv")
            .setVerifiedClaims(Map.of("claims", Map.of("given_name", "Alice")));

    Map<String, Object> context = ExternalRequestUserContextCreator.create(user);

    assertFalse(context.containsKey("hashed_password"));
    assertFalse(context.containsKey("raw_password"));
    assertFalse(context.containsKey("credentials"));
    assertFalse(context.containsKey("verified_claims"));
    assertFalse(context.containsKey("status"));
    assertFalse(context.containsKey("has_password"));
    assertFalse(context.containsKey("permissions"));
  }

  @Test
  @DisplayName("a missing user yields an empty projection")
  void missingUser() {
    assertTrue(ExternalRequestUserContextCreator.create(null).isEmpty());
    assertTrue(ExternalRequestUserContextCreator.create(User.notFound()).isEmpty());
  }
}

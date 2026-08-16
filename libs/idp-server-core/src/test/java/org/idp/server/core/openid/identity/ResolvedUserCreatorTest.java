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

package org.idp.server.core.openid.identity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins what an external source may and may not change about a user it resolves on a 1st factor
 * (#1792).
 *
 * <p>Four interactors resolve a 1st factor this way — federation, external-api, external-password
 * and external-token — and all of them go through {@link ResolvedUserCreator}. These tests are what
 * keeps the rule from being quietly dropped for one of them.
 */
class ResolvedUserCreatorTest {

  private static User storedUser() {
    User user = new User();
    user.setSub("stored-sub");
    user.setProviderId("stored-provider");
    user.setExternalUserId("stored-external-id");
    user.setStatus(UserStatus.REGISTERED);
    user.setEmail("stored@example.com");
    HashMap<String, Object> customProperties = new HashMap<>();
    customProperties.put("rank", "gold");
    user.setCustomProperties(customProperties);
    return user;
  }

  @Nested
  class WhatTheExternalSourceMayNotChange {

    @Test
    void statusStaysTheStoredOneEvenWhenTheMappingProducesOne() {
      // SECURITY: user_mapping_rules is operator-authored but fed by an external response, and
      // User#updateWith takes status from the patch whenever the patch has one. Without the pin in
      // ResolvedUserCreator, a rule writing status would revive a locked account.
      User locked = storedUser();
      locked.setStatus(UserStatus.LOCKED);
      User mapped = new User();
      mapped.setStatus(UserStatus.REGISTERED);

      User resolved = ResolvedUserCreator.create(locked, mapped);

      assertEquals(UserStatus.LOCKED, resolved.status());
    }

    @Test
    void identifiersStayTheStoredOnes() {
      // A mapping rule reading $.user could otherwise make "the key we searched by" and "the key we
      // store" disagree. updateWith treats these three as immutable, which is what basing the
      // result on the stored user buys.
      User mapped = new User();
      mapped.setSub("mapped-sub");
      mapped.setProviderId("mapped-provider");
      mapped.setExternalUserId("mapped-external-id");

      User resolved = ResolvedUserCreator.create(storedUser(), mapped);

      assertEquals("stored-sub", resolved.sub());
      assertEquals("stored-provider", resolved.providerId());
      assertEquals("stored-external-id", resolved.externalUserId());
    }
  }

  @Nested
  class WhatSurvivesFromTheStoredUser {

    @Test
    void attributesTheMappingDidNotRestateAreKept() {
      // The whole point of #1792: the grant snapshots this user, so anything dropped here is
      // missing from that session's tokens while the database and UserInfo still have it.
      User mapped = new User();
      mapped.setEmail("fresh@example.com");

      User resolved = ResolvedUserCreator.create(storedUser(), mapped);

      assertEquals("fresh@example.com", resolved.email());
      assertEquals("gold", resolved.customPropertiesValue().get("rank"));
    }

    @Test
    void rolesTheMappingDidNotProduceAreKept() {
      User stored = storedUser();
      stored.setRoles(List.of(new UserRole("role-1", "admin")));

      User resolved = ResolvedUserCreator.create(stored, new User());

      assertEquals(1, resolved.roles().size());
      assertEquals("admin", resolved.roles().get(0).roleName());
    }
  }

  @Nested
  class WhatTheExternalSourceMayChange {

    @Test
    void producedStandardClaimsOverwriteTheStoredOnes() {
      // Resolution doubles as a sync from the system of record, so a value the source restates
      // wins.
      User mapped = new User();
      mapped.setName("New Name");

      User resolved = ResolvedUserCreator.create(storedUser(), mapped);

      assertEquals("New Name", resolved.name());
    }

    @Test
    void producedCustomPropertiesMergeRatherThanReplace() {
      HashMap<String, Object> produced = new HashMap<>();
      produced.put("department", "sales");
      User mapped = new User();
      mapped.setCustomProperties(produced);

      User resolved = ResolvedUserCreator.create(storedUser(), mapped);

      assertEquals("sales", resolved.customPropertiesValue().get("department"));
      assertEquals("gold", resolved.customPropertiesValue().get("rank"));
    }
  }
}

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

package org.idp.server.core.openid.identity.permission;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PermissionMatcherTest {

  @Nested
  @DisplayName("matches() - Single permission matching")
  class MatchesTests {

    @Test
    @DisplayName("Should match exact permission")
    void shouldMatchExact() {
      assertTrue(PermissionMatcher.matches("idp:user:create", "idp:user:create"));
      assertTrue(PermissionMatcher.matches("idp:organization:read", "idp:organization:read"));
    }

    @Test
    @DisplayName("Should not match different permission")
    void shouldNotMatchDifferent() {
      assertFalse(PermissionMatcher.matches("idp:user:create", "idp:user:delete"));
      assertFalse(PermissionMatcher.matches("idp:user:read", "idp:organization:read"));
    }

    @Test
    @DisplayName("Global wildcard should match everything")
    void globalWildcardShouldMatchEverything() {
      assertTrue(PermissionMatcher.matches("*", "idp:user:create"));
      assertTrue(PermissionMatcher.matches("*", "idp:organization:delete"));
      assertTrue(PermissionMatcher.matches("*", "custom:feature:admin"));
      assertTrue(PermissionMatcher.matches("*", "anything"));
    }

    @Test
    @DisplayName("Namespace wildcard should match all permissions in namespace")
    void namespaceWildcardShouldMatchNamespace() {
      assertTrue(PermissionMatcher.matches("idp:*", "idp:user:create"));
      assertTrue(PermissionMatcher.matches("idp:*", "idp:organization:delete"));
      assertTrue(PermissionMatcher.matches("idp:*", "idp:tenant:read"));
      assertTrue(PermissionMatcher.matches("idp:*", "idp:system:write"));
    }

    @Test
    @DisplayName("Namespace wildcard should not match other namespaces")
    void namespaceWildcardShouldNotMatchOther() {
      assertFalse(PermissionMatcher.matches("idp:*", "custom:feature:admin"));
      assertFalse(PermissionMatcher.matches("idp:*", "myapp:document:read"));
    }

    @Test
    @DisplayName("Resource wildcard should match all actions for resource")
    void resourceWildcardShouldMatchResource() {
      assertTrue(PermissionMatcher.matches("idp:user:*", "idp:user:create"));
      assertTrue(PermissionMatcher.matches("idp:user:*", "idp:user:read"));
      assertTrue(PermissionMatcher.matches("idp:user:*", "idp:user:update"));
      assertTrue(PermissionMatcher.matches("idp:user:*", "idp:user:delete"));
    }

    @Test
    @DisplayName("Resource wildcard should not match other resources")
    void resourceWildcardShouldNotMatchOtherResource() {
      assertFalse(PermissionMatcher.matches("idp:user:*", "idp:organization:create"));
      assertFalse(PermissionMatcher.matches("idp:user:*", "idp:tenant:read"));
    }

    @Test
    @DisplayName("Should handle null values")
    void shouldHandleNull() {
      assertFalse(PermissionMatcher.matches(null, "idp:user:create"));
      assertFalse(PermissionMatcher.matches("idp:user:create", null));
      assertFalse(PermissionMatcher.matches(null, null));
    }
  }

  @Nested
  @DisplayName("matchesAny() - Multiple user permissions")
  class MatchesAnyTests {

    @Test
    @DisplayName("Should match when one permission matches exactly")
    void shouldMatchWhenOneMatches() {
      Set<String> userPermissions = Set.of("idp:user:read", "idp:user:create");
      assertTrue(PermissionMatcher.matchesAny(userPermissions, "idp:user:create"));
    }

    @Test
    @DisplayName("Should match when wildcard covers required permission")
    void shouldMatchWithWildcard() {
      Set<String> userPermissions = Set.of("idp:user:*");
      assertTrue(PermissionMatcher.matchesAny(userPermissions, "idp:user:create"));
      assertTrue(PermissionMatcher.matchesAny(userPermissions, "idp:user:delete"));
    }

    @Test
    @DisplayName("Should match when global wildcard is present")
    void shouldMatchWithGlobalWildcard() {
      Set<String> userPermissions = Set.of("*");
      assertTrue(PermissionMatcher.matchesAny(userPermissions, "idp:user:create"));
      assertTrue(PermissionMatcher.matchesAny(userPermissions, "custom:feature:admin"));
    }

    @Test
    @DisplayName("Should not match when no permission covers required")
    void shouldNotMatchWhenNone() {
      Set<String> userPermissions = Set.of("idp:user:read", "idp:organization:read");
      assertFalse(PermissionMatcher.matchesAny(userPermissions, "idp:user:create"));
    }

    @Test
    @DisplayName("Should handle empty user permissions")
    void shouldHandleEmptyPermissions() {
      assertFalse(PermissionMatcher.matchesAny(Set.of(), "idp:user:create"));
      assertFalse(PermissionMatcher.matchesAny(null, "idp:user:create"));
    }
  }

  @Nested
  @DisplayName("matchesAll() - Multiple required permissions")
  class MatchesAllTests {

    @Test
    @DisplayName("Should match when all required permissions are covered")
    void shouldMatchAll() {
      Set<String> userPermissions = Set.of("idp:user:create", "idp:user:read");
      Set<String> required = Set.of("idp:user:create", "idp:user:read");
      assertTrue(PermissionMatcher.matchesAll(userPermissions, required));
    }

    @Test
    @DisplayName("Should match when wildcard covers all required permissions")
    void shouldMatchAllWithWildcard() {
      Set<String> userPermissions = Set.of("idp:user:*");
      Set<String> required = Set.of("idp:user:create", "idp:user:read", "idp:user:delete");
      assertTrue(PermissionMatcher.matchesAll(userPermissions, required));
    }

    @Test
    @DisplayName("Should match when global wildcard is present")
    void shouldMatchAllWithGlobalWildcard() {
      Set<String> userPermissions = Set.of("*");
      Set<String> required =
          Set.of("idp:user:create", "idp:organization:delete", "custom:feature:admin");
      assertTrue(PermissionMatcher.matchesAll(userPermissions, required));
    }

    @Test
    @DisplayName("Should not match when missing required permission")
    void shouldNotMatchWhenMissing() {
      Set<String> userPermissions = Set.of("idp:user:read");
      Set<String> required = Set.of("idp:user:create", "idp:user:read");
      assertFalse(PermissionMatcher.matchesAll(userPermissions, required));
    }

    @Test
    @DisplayName("Should match when required is empty")
    void shouldMatchEmptyRequired() {
      Set<String> userPermissions = Set.of("idp:user:read");
      assertTrue(PermissionMatcher.matchesAll(userPermissions, Set.of()));
      assertTrue(PermissionMatcher.matchesAll(userPermissions, null));
    }

    @Test
    @DisplayName("Should not match when user has no permissions")
    void shouldNotMatchEmptyUser() {
      Set<String> required = Set.of("idp:user:create");
      assertFalse(PermissionMatcher.matchesAll(Set.of(), required));
      assertFalse(PermissionMatcher.matchesAll(null, required));
    }

    @Test
    @DisplayName("Should handle mixed wildcards and exact permissions")
    void shouldHandleMixedPermissions() {
      Set<String> userPermissions = Set.of("idp:user:*", "idp:organization:read");
      Set<String> required = Set.of("idp:user:create", "idp:user:delete", "idp:organization:read");
      assertTrue(PermissionMatcher.matchesAll(userPermissions, required));
    }
  }

  @Nested
  @DisplayName("Utility methods")
  class UtilityTests {

    @Test
    @DisplayName("isWildcard should detect wildcard permissions")
    void isWildcardShouldDetect() {
      assertTrue(PermissionMatcher.isWildcard("*"));
      assertTrue(PermissionMatcher.isWildcard("idp:*"));
      assertTrue(PermissionMatcher.isWildcard("idp:user:*"));
      assertFalse(PermissionMatcher.isWildcard("idp:user:create"));
      assertFalse(PermissionMatcher.isWildcard(null));
    }

    @Test
    @DisplayName("isControlPlanePermission should detect idp namespace")
    void isControlPlanePermissionShouldDetect() {
      assertTrue(PermissionMatcher.isControlPlanePermission("idp:user:create"));
      assertTrue(PermissionMatcher.isControlPlanePermission("idp:organization:read"));
      assertFalse(PermissionMatcher.isControlPlanePermission("custom:feature:admin"));
      assertFalse(PermissionMatcher.isControlPlanePermission(null));
    }
  }

  @Nested
  @DisplayName("Backward compatibility - Legacy format support")
  class BackwardCompatibilityTests {

    @Test
    @DisplayName("Should normalize legacy permission to new format")
    void shouldNormalizeLegacyPermission() {
      assertEquals("idp:organization:create", PermissionMatcher.normalize("organization:create"));
      assertEquals("idp:user:read", PermissionMatcher.normalize("user:read"));
      assertEquals("idp:tenant:delete", PermissionMatcher.normalize("tenant:delete"));
      // admin_user is normalized to admin-user (underscore to hyphen)
      assertEquals("idp:admin-user:create", PermissionMatcher.normalize("admin_user:create"));
    }

    @Test
    @DisplayName("Should not normalize already namespaced permission")
    void shouldNotNormalizeNamespacedPermission() {
      assertEquals("idp:user:create", PermissionMatcher.normalize("idp:user:create"));
      assertEquals("custom:feature:admin", PermissionMatcher.normalize("custom:feature:admin"));
    }

    @Test
    @DisplayName("Legacy user permission should match new format required permission")
    void legacyUserShouldMatchNewRequired() {
      // User has legacy format, API requires new format
      assertTrue(PermissionMatcher.matches("organization:create", "idp:organization:create"));
      assertTrue(PermissionMatcher.matches("user:read", "idp:user:read"));
    }

    @Test
    @DisplayName("New format user permission should match legacy required permission")
    void newUserShouldMatchLegacyRequired() {
      // User has new format, required permission is legacy (edge case)
      assertTrue(PermissionMatcher.matches("idp:organization:create", "organization:create"));
      assertTrue(PermissionMatcher.matches("idp:user:read", "user:read"));
    }

    @Test
    @DisplayName("Wildcard should match legacy format permissions")
    void wildcardShouldMatchLegacy() {
      assertTrue(PermissionMatcher.matches("idp:*", "organization:create"));
      assertTrue(PermissionMatcher.matches("idp:*", "user:read"));
      assertTrue(PermissionMatcher.matches("idp:user:*", "user:create"));
    }

    @Test
    @DisplayName("Legacy wildcard-like permission should work")
    void legacyWildcardShouldWork() {
      // If someone stored "user:*" as legacy format
      assertTrue(PermissionMatcher.matches("user:*", "idp:user:create"));
      assertTrue(PermissionMatcher.matches("user:*", "user:read"));
    }

    @Test
    @DisplayName("matchesAll should work with mixed legacy and new format")
    void matchesAllWithMixedFormats() {
      Set<String> userPermissions = Set.of("organization:create", "idp:user:read");
      Set<String> required = Set.of("idp:organization:create", "user:read");
      assertTrue(PermissionMatcher.matchesAll(userPermissions, required));
    }

    @Test
    @DisplayName("Should not normalize custom namespace permissions")
    void shouldNotNormalizeCustomNamespace() {
      // Custom permissions should remain unchanged
      assertEquals("myapp:document:read", PermissionMatcher.normalize("myapp:document:read"));
      assertFalse(PermissionMatcher.matches("idp:*", "myapp:document:read"));
    }
  }
}

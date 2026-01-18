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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PermissionNamespaceValidatorTest {

  @Nested
  @DisplayName("validate() - Permission name validation")
  class ValidateTests {

    @Test
    @DisplayName("Should throw exception for reserved namespace 'idp'")
    void shouldThrowForReservedNamespace() {
      ReservedNamespaceException ex =
          assertThrows(
              ReservedNamespaceException.class,
              () -> PermissionNamespaceValidator.validate("idp:custom:action"));

      assertEquals("idp:custom:action", ex.permissionName());
      assertEquals("idp", ex.namespace());
    }

    @Test
    @DisplayName("Should not throw for custom namespace")
    void shouldNotThrowForCustomNamespace() {
      assertDoesNotThrow(() -> PermissionNamespaceValidator.validate("myapp:document:read"));
      assertDoesNotThrow(() -> PermissionNamespaceValidator.validate("custom:feature:admin"));
      assertDoesNotThrow(() -> PermissionNamespaceValidator.validate("org:project:manage"));
    }

    @Test
    @DisplayName("Should not throw for permission without namespace")
    void shouldNotThrowWithoutNamespace() {
      assertDoesNotThrow(() -> PermissionNamespaceValidator.validate("simplePermission"));
    }

    @Test
    @DisplayName("Should handle null and empty values")
    void shouldHandleNullAndEmpty() {
      assertDoesNotThrow(() -> PermissionNamespaceValidator.validate(null));
      assertDoesNotThrow(() -> PermissionNamespaceValidator.validate(""));
    }
  }

  @Nested
  @DisplayName("usesReservedNamespace() - Check reserved namespace usage")
  class UsesReservedNamespaceTests {

    @Test
    @DisplayName("Should return true for reserved namespace")
    void shouldReturnTrueForReserved() {
      assertTrue(PermissionNamespaceValidator.usesReservedNamespace("idp:user:create"));
      assertTrue(PermissionNamespaceValidator.usesReservedNamespace("idp:organization:read"));
    }

    @Test
    @DisplayName("Should return false for custom namespace")
    void shouldReturnFalseForCustom() {
      assertFalse(PermissionNamespaceValidator.usesReservedNamespace("custom:feature:admin"));
      assertFalse(PermissionNamespaceValidator.usesReservedNamespace("myapp:document:read"));
    }

    @Test
    @DisplayName("Should return false for null and empty")
    void shouldReturnFalseForNullAndEmpty() {
      assertFalse(PermissionNamespaceValidator.usesReservedNamespace(null));
      assertFalse(PermissionNamespaceValidator.usesReservedNamespace(""));
    }
  }

  @Nested
  @DisplayName("extractNamespace() - Extract namespace from permission name")
  class ExtractNamespaceTests {

    @Test
    @DisplayName("Should extract namespace correctly")
    void shouldExtractNamespace() {
      assertEquals("idp", PermissionNamespaceValidator.extractNamespace("idp:user:create"));
      assertEquals("custom", PermissionNamespaceValidator.extractNamespace("custom:feature:admin"));
      assertEquals("myapp", PermissionNamespaceValidator.extractNamespace("myapp:document:read"));
    }

    @Test
    @DisplayName("Should return empty for permission without namespace")
    void shouldReturnEmptyWithoutNamespace() {
      assertEquals("", PermissionNamespaceValidator.extractNamespace("simplePermission"));
      assertEquals("", PermissionNamespaceValidator.extractNamespace(null));
    }
  }

  @Nested
  @DisplayName("reservedNamespaces() - Get reserved namespaces")
  class ReservedNamespacesTests {

    @Test
    @DisplayName("Should contain 'idp' namespace")
    void shouldContainIdpNamespace() {
      assertTrue(PermissionNamespaceValidator.reservedNamespaces().contains("idp"));
    }
  }
}

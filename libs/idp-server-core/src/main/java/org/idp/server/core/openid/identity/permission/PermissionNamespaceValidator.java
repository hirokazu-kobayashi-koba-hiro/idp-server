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

import java.util.Set;

/**
 * Validates permission names against reserved namespaces.
 *
 * <p>Prevents custom permissions from using system-reserved prefixes like {@code idp:}.
 *
 * <p>Reserved namespaces:
 *
 * <ul>
 *   <li>{@code idp:} - Control plane permissions (organization, tenant, user management, etc.)
 * </ul>
 *
 * <p>Custom permissions should use their own namespace like:
 *
 * <ul>
 *   <li>{@code myapp:document:read}
 *   <li>{@code custom:feature:admin}
 * </ul>
 */
public class PermissionNamespaceValidator {

  private static final Set<String> RESERVED_NAMESPACES = Set.of("idp");
  private static final String NAMESPACE_SEPARATOR = ":";

  private PermissionNamespaceValidator() {}

  /**
   * Validates that the permission name does not use reserved namespaces.
   *
   * @param permissionName the permission name to validate
   * @throws ReservedNamespaceException if the permission uses a reserved namespace
   */
  public static void validate(String permissionName) {
    if (permissionName == null || permissionName.isEmpty()) {
      return;
    }

    String namespace = extractNamespace(permissionName);
    if (RESERVED_NAMESPACES.contains(namespace)) {
      throw new ReservedNamespaceException(permissionName, namespace);
    }
  }

  /**
   * Checks if the permission name uses a reserved namespace.
   *
   * @param permissionName the permission name to check
   * @return true if the permission uses a reserved namespace
   */
  public static boolean usesReservedNamespace(String permissionName) {
    if (permissionName == null || permissionName.isEmpty()) {
      return false;
    }

    String namespace = extractNamespace(permissionName);
    return RESERVED_NAMESPACES.contains(namespace);
  }

  /**
   * Extracts the namespace from a permission name.
   *
   * @param permissionName the permission name (e.g., "idp:user:create")
   * @return the namespace (e.g., "idp"), or empty string if no namespace
   */
  public static String extractNamespace(String permissionName) {
    if (permissionName == null || !permissionName.contains(NAMESPACE_SEPARATOR)) {
      return "";
    }

    int firstSeparator = permissionName.indexOf(NAMESPACE_SEPARATOR);
    return permissionName.substring(0, firstSeparator);
  }

  /**
   * Returns the set of reserved namespaces.
   *
   * @return immutable set of reserved namespace names
   */
  public static Set<String> reservedNamespaces() {
    return RESERVED_NAMESPACES;
  }
}

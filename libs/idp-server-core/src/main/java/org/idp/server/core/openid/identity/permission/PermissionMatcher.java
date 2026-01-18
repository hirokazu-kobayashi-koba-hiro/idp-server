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
 * Utility class for permission wildcard matching.
 *
 * <p>Supports AWS IAM-style wildcard patterns:
 *
 * <ul>
 *   <li>{@code *} - matches all permissions
 *   <li>{@code idp:*} - matches all control plane permissions (idp:user:create,
 *       idp:organization:delete, etc.)
 *   <li>{@code idp:user:*} - matches all user management permissions (idp:user:create,
 *       idp:user:read, etc.)
 * </ul>
 *
 * <p>The wildcard character {@code *} only works at the end of a permission pattern.
 */
public class PermissionMatcher {

  private static final String WILDCARD = "*";
  private static final String NAMESPACE_SEPARATOR = ":";
  public static final String CONTROL_PLANE_NAMESPACE = "idp";
  private static final String CONTROL_PLANE_PREFIX = CONTROL_PLANE_NAMESPACE + NAMESPACE_SEPARATOR;

  private PermissionMatcher() {}

  /**
   * Checks if the user permission matches the required permission.
   *
   * <p>This method normalizes both permissions for backward compatibility, converting legacy format
   * (e.g., "organization:create") to new format (e.g., "idp:organization:create").
   *
   * @param userPermission the permission that the user has (may contain wildcards)
   * @param requiredPermission the permission required for the operation
   * @return true if user permission covers the required permission
   */
  public static boolean matches(String userPermission, String requiredPermission) {
    if (userPermission == null || requiredPermission == null) {
      return false;
    }

    // Normalize both permissions for backward compatibility
    String normalizedUser = normalize(userPermission);
    String normalizedRequired = normalize(requiredPermission);

    // Exact match
    if (normalizedUser.equals(normalizedRequired)) {
      return true;
    }

    // Global wildcard matches everything
    if (WILDCARD.equals(normalizedUser)) {
      return true;
    }

    // Pattern wildcard matching (e.g., "idp:*", "idp:user:*")
    if (normalizedUser.endsWith(NAMESPACE_SEPARATOR + WILDCARD)) {
      String prefix = normalizedUser.substring(0, normalizedUser.length() - 1);
      return normalizedRequired.startsWith(prefix);
    }

    return false;
  }

  /**
   * Normalizes a permission string for backward compatibility.
   *
   * <p>Converts legacy format permissions (without "idp:" prefix) to the new namespaced format. For
   * example:
   *
   * <ul>
   *   <li>"organization:create" → "idp:organization:create"
   *   <li>"user:read" → "idp:user:read"
   *   <li>"idp:user:read" → "idp:user:read" (unchanged)
   *   <li>"custom:feature:admin" → "custom:feature:admin" (unchanged, different namespace)
   * </ul>
   *
   * @param permission the permission to normalize
   * @return normalized permission string
   */
  public static String normalize(String permission) {
    if (permission == null || permission.isEmpty()) {
      return permission;
    }

    // Already has a namespace prefix
    if (permission.startsWith(CONTROL_PLANE_PREFIX)) {
      return permission;
    }

    // Global wildcard - no normalization needed
    if (WILDCARD.equals(permission)) {
      return permission;
    }

    // Check if it looks like a legacy control plane permission (resource:action format)
    // Legacy format: "organization:create", "user:read", etc.
    // Non-legacy format: "custom:feature:admin" (has its own namespace)
    if (isLegacyControlPlanePermission(permission)) {
      // Also normalize underscore to hyphen (admin_user -> admin-user)
      String normalized = permission.replace("_", "-");
      return CONTROL_PLANE_PREFIX + normalized;
    }

    return permission;
  }

  /**
   * Checks if the permission appears to be a legacy control plane permission.
   *
   * <p>Legacy permissions have format "resource:action" without a namespace prefix. This method
   * checks against known control plane resource names.
   *
   * @param permission the permission to check
   * @return true if it appears to be a legacy control plane permission
   */
  private static boolean isLegacyControlPlanePermission(String permission) {
    if (!permission.contains(NAMESPACE_SEPARATOR)) {
      return false;
    }

    // Known legacy resource prefixes
    String[] legacyResources = {
      "organization:",
      "tenant:",
      "tenant-invitation:",
      "authorization-server:",
      "client:",
      "user:",
      "admin_user:", // legacy format used underscore
      "admin-user:",
      "permission:",
      "role:",
      "authentication-config:",
      "authentication-policy-config:",
      "identity-verification-config:",
      "federation-config:",
      "security-event-hook-config:",
      "security-event-hook:",
      "security-event:",
      "audit-log:",
      "authentication-transaction:",
      "authentication-interaction:",
      "session:",
      "system:"
    };

    for (String resource : legacyResources) {
      if (permission.startsWith(resource)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Checks if any of the user's permissions match the required permission.
   *
   * @param userPermissions set of permissions the user has
   * @param requiredPermission the permission required for the operation
   * @return true if any user permission covers the required permission
   */
  public static boolean matchesAny(Set<String> userPermissions, String requiredPermission) {
    if (userPermissions == null || userPermissions.isEmpty()) {
      return false;
    }

    for (String userPermission : userPermissions) {
      if (matches(userPermission, requiredPermission)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if user has all required permissions (considering wildcards).
   *
   * @param userPermissions set of permissions the user has
   * @param requiredPermissions set of permissions required for the operation
   * @return true if user permissions cover all required permissions
   */
  public static boolean matchesAll(Set<String> userPermissions, Set<String> requiredPermissions) {
    if (requiredPermissions == null || requiredPermissions.isEmpty()) {
      return true;
    }

    if (userPermissions == null || userPermissions.isEmpty()) {
      return false;
    }

    for (String required : requiredPermissions) {
      if (!matchesAny(userPermissions, required)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks if the permission is a wildcard permission.
   *
   * @param permission the permission to check
   * @return true if the permission contains a wildcard
   */
  public static boolean isWildcard(String permission) {
    return permission != null && permission.contains(WILDCARD);
  }

  /**
   * Checks if the permission belongs to the control plane namespace.
   *
   * @param permission the permission to check
   * @return true if the permission starts with "idp:"
   */
  public static boolean isControlPlanePermission(String permission) {
    return permission != null
        && permission.startsWith(CONTROL_PLANE_NAMESPACE + NAMESPACE_SEPARATOR);
  }
}

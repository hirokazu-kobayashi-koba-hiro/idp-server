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

package org.idp.server.control_plane.base.definition;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Organization-level admin permissions container.
 *
 * <p>This class manages a set of {@link OrganizationAdminPermission} values and provides
 * convenience methods for permission validation and string representation.
 *
 * <p>Organization-level permissions are scoped to specific organizations and allow organization
 * administrators (ORGANIZER tenant type) to manage resources within their organization boundaries.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * Set<OrganizationAdminPermission> permissions = Set.of(
 *     OrganizationAdminPermission.ORG_TENANT_CREATE,
 *     OrganizationAdminPermission.ORG_TENANT_READ
 * );
 * OrganizationAdminPermissions adminPerms = new OrganizationAdminPermissions(permissions);
 *
 * // Validate user permissions
 * if (adminPerms.includesAll(user.permissionsAsSet())) {
 *     // User has required permissions
 * }
 * }</pre>
 *
 * @see OrganizationAdminPermission
 * @see org.idp.server.control_plane.organization.access.OrganizationAccessVerifier
 */
public class OrganizationAdminPermissions {
  Set<OrganizationAdminPermission> values;

  /**
   * Constructs a new OrganizationAdminPermissions with the given permission set.
   *
   * @param values the set of organization admin permissions
   */
  public OrganizationAdminPermissions(Set<OrganizationAdminPermission> values) {
    this.values = values;
  }

  /**
   * Returns the permission values as a set of strings.
   *
   * @return set of permission value strings
   */
  public Set<String> valuesAsSetString() {
    return values.stream().map(OrganizationAdminPermission::value).collect(Collectors.toSet());
  }

  /**
   * Returns the permission values as a comma-separated string.
   *
   * @return comma-separated permission value string
   */
  public String valuesAsString() {
    return values.stream().map(OrganizationAdminPermission::value).collect(Collectors.joining(","));
  }

  /**
   * Validates that the user has all required permissions.
   *
   * <p>This method checks if the user's permission set contains all the permissions required by
   * this OrganizationAdminPermissions instance.
   *
   * @param userPermissions the user's permission set as strings
   * @return true if user has all required permissions, false otherwise
   */
  public boolean includesAll(Set<String> userPermissions) {
    return userPermissions.containsAll(valuesAsSetString());
  }
}

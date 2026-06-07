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

package org.idp.server.platform.multi_tenancy.tenant.policy;

import java.util.HashMap;
import java.util.Map;

/**
 * Controls which user attribute associations are loaded by {@code UserQueryRepository} for a given
 * tenant.
 *
 * <p>Each lookup in {@code UserQueryDataSource} optionally enriches the result with extra JOIN /
 * sub-query work to fetch {@code assigned_organizations}, {@code assigned_tenants}, {@code roles},
 * and {@code permissions} data. Those are wasted work for tenants that do not use the corresponding
 * features (Organization Access Control, multi-tenant user assignment, RBAC).
 *
 * <p>By disabling the relevant flag here, the cost is skipped (either a separate {@code SELECT} for
 * assigned data, or a {@code LEFT JOIN} + {@code GROUP BY} in the main user query for RBAC). {@code
 * ModelConverter} treats absent keys as empty collections, so callers receive a {@link
 * org.idp.server.core.openid.identity.User} with empty associations instead.
 *
 * <p>Defaults are {@code true} for backward compatibility — existing tenants behave exactly as
 * before unless they opt out.
 *
 * @see TenantIdentityPolicy
 */
public class UserAttributeLoadRule {

  private static final boolean DEFAULT_INCLUDE_ASSIGNED_ORGANIZATIONS = true;
  private static final boolean DEFAULT_INCLUDE_ASSIGNED_TENANTS = true;
  private static final boolean DEFAULT_INCLUDE_ROLES = true;
  private static final boolean DEFAULT_INCLUDE_PERMISSIONS = true;

  private boolean includeAssignedOrganizations;
  private boolean includeAssignedTenants;
  private boolean includeRoles;
  private boolean includePermissions;

  public UserAttributeLoadRule() {
    this.includeAssignedOrganizations = DEFAULT_INCLUDE_ASSIGNED_ORGANIZATIONS;
    this.includeAssignedTenants = DEFAULT_INCLUDE_ASSIGNED_TENANTS;
    this.includeRoles = DEFAULT_INCLUDE_ROLES;
    this.includePermissions = DEFAULT_INCLUDE_PERMISSIONS;
  }

  public UserAttributeLoadRule(
      boolean includeAssignedOrganizations,
      boolean includeAssignedTenants,
      boolean includeRoles,
      boolean includePermissions) {
    this.includeAssignedOrganizations = includeAssignedOrganizations;
    this.includeAssignedTenants = includeAssignedTenants;
    this.includeRoles = includeRoles;
    this.includePermissions = includePermissions;
  }

  /**
   * Default rule: load all associations.
   *
   * <p>Matches the historical behavior of {@code UserQueryDataSource} prior to this option being
   * introduced.
   */
  public static UserAttributeLoadRule defaultRule() {
    return new UserAttributeLoadRule();
  }

  /**
   * Creates a rule from a configuration map. Unknown / missing keys fall back to defaults.
   *
   * @param map configuration map
   * @return user attribute load rule
   */
  public static UserAttributeLoadRule fromMap(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return defaultRule();
    }

    boolean includeAssignedOrganizations =
        readBoolean(map, "include_assigned_organizations", DEFAULT_INCLUDE_ASSIGNED_ORGANIZATIONS);
    boolean includeAssignedTenants =
        readBoolean(map, "include_assigned_tenants", DEFAULT_INCLUDE_ASSIGNED_TENANTS);
    boolean includeRoles = readBoolean(map, "include_roles", DEFAULT_INCLUDE_ROLES);
    boolean includePermissions =
        readBoolean(map, "include_permissions", DEFAULT_INCLUDE_PERMISSIONS);

    return new UserAttributeLoadRule(
        includeAssignedOrganizations, includeAssignedTenants, includeRoles, includePermissions);
  }

  private static boolean readBoolean(Map<String, Object> map, String key, boolean fallback) {
    if (!map.containsKey(key)) {
      return fallback;
    }
    Object value = map.get(key);
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    return fallback;
  }

  /** Whether {@code assigned_organizations} should be loaded. */
  public boolean includeAssignedOrganizations() {
    return includeAssignedOrganizations;
  }

  /** Whether {@code assigned_tenants} should be loaded. */
  public boolean includeAssignedTenants() {
    return includeAssignedTenants;
  }

  /**
   * Whether {@code roles} should be loaded (via JOIN with {@code idp_user_roles} + {@code role}).
   */
  public boolean includeRoles() {
    return includeRoles;
  }

  /**
   * Whether {@code permissions} should be loaded (via JOIN with {@code role_permission} + {@code
   * permission}).
   *
   * <p>Permissions are reachable only via the role join chain, so disabling roles while keeping
   * permissions still requires the role intermediate tables to be joined at runtime.
   */
  public boolean includePermissions() {
    return includePermissions;
  }

  /**
   * Whether the role join chain ({@code idp_user_roles} + {@code role}) is needed in the main user
   * SELECT.
   *
   * <p>True if either roles or permissions are requested (permissions need the role tables as
   * intermediates).
   */
  public boolean needsRoleJoin() {
    return includeRoles || includePermissions;
  }

  /**
   * Converts this rule to a Map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("include_assigned_organizations", includeAssignedOrganizations);
    map.put("include_assigned_tenants", includeAssignedTenants);
    map.put("include_roles", includeRoles);
    map.put("include_permissions", includePermissions);
    return map;
  }
}

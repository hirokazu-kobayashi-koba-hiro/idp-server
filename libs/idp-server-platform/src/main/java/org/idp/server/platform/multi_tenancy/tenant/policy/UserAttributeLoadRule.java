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
 * <p>Each lookup in {@code UserQueryDataSource} optionally enriches the result with extra JOIN
 * queries that fetch {@code assigned_organizations} / {@code assigned_tenants} data. Those queries
 * are wasted work for tenants that do not use Organization Access Control or multi-tenant user
 * assignment.
 *
 * <p>By disabling the corresponding flag here, the {@code SELECT assigned_*} round-trips are
 * skipped entirely. {@code ModelConverter} treats absent keys as empty collections, so callers
 * receive a {@link org.idp.server.core.openid.identity.User} with empty assigned data instead.
 *
 * <p>Defaults are {@code true} for backward compatibility — existing tenants behave exactly as
 * before unless they opt out.
 *
 * @see TenantIdentityPolicy
 */
public class UserAttributeLoadRule {

  private static final boolean DEFAULT_INCLUDE_ASSIGNED_ORGANIZATIONS = true;
  private static final boolean DEFAULT_INCLUDE_ASSIGNED_TENANTS = true;

  private boolean includeAssignedOrganizations;
  private boolean includeAssignedTenants;

  public UserAttributeLoadRule() {
    this.includeAssignedOrganizations = DEFAULT_INCLUDE_ASSIGNED_ORGANIZATIONS;
    this.includeAssignedTenants = DEFAULT_INCLUDE_ASSIGNED_TENANTS;
  }

  public UserAttributeLoadRule(
      boolean includeAssignedOrganizations, boolean includeAssignedTenants) {
    this.includeAssignedOrganizations = includeAssignedOrganizations;
    this.includeAssignedTenants = includeAssignedTenants;
  }

  /**
   * Default rule: load all assigned associations.
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

    boolean includeAssignedOrganizations = DEFAULT_INCLUDE_ASSIGNED_ORGANIZATIONS;
    if (map.containsKey("include_assigned_organizations")) {
      Object value = map.get("include_assigned_organizations");
      if (value instanceof Boolean) {
        includeAssignedOrganizations = (Boolean) value;
      }
    }

    boolean includeAssignedTenants = DEFAULT_INCLUDE_ASSIGNED_TENANTS;
    if (map.containsKey("include_assigned_tenants")) {
      Object value = map.get("include_assigned_tenants");
      if (value instanceof Boolean) {
        includeAssignedTenants = (Boolean) value;
      }
    }

    return new UserAttributeLoadRule(includeAssignedOrganizations, includeAssignedTenants);
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
   * Converts this rule to a Map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("include_assigned_organizations", includeAssignedOrganizations);
    map.put("include_assigned_tenants", includeAssignedTenants);
    return map;
  }
}

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

package org.idp.server.platform.multi_tenancy.organization;

import java.util.HashMap;
import org.idp.server.platform.configuration.Configurable;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class Organization implements Configurable {
  OrganizationIdentifier identifier;
  OrganizationName name;
  OrganizationDescription description;
  AssignedTenants assignedTenants;
  boolean enabled = true;

  public Organization() {}

  public Organization(
      OrganizationIdentifier identifier,
      OrganizationName name,
      OrganizationDescription description) {
    this(identifier, name, description, new AssignedTenants(), true);
  }

  public Organization(
      OrganizationIdentifier identifier,
      OrganizationName name,
      OrganizationDescription description,
      AssignedTenants assignedTenants) {
    this(identifier, name, description, assignedTenants, true);
  }

  public Organization(
      OrganizationIdentifier identifier,
      OrganizationName name,
      OrganizationDescription description,
      AssignedTenants assignedTenants,
      boolean enabled) {
    this.identifier = identifier;
    this.name = name;
    this.description = description;
    this.assignedTenants = assignedTenants;
    this.enabled = enabled;
  }

  public OrganizationIdentifier identifier() {
    return identifier;
  }

  public OrganizationName name() {
    return name;
  }

  public OrganizationDescription description() {
    return description;
  }

  public AssignedTenants assignedTenants() {
    return assignedTenants;
  }

  public Organization updateWithTenant(AssignedTenant assignedTenant) {
    AssignedTenants addedTenants = assignedTenants.add(assignedTenant);
    return new Organization(identifier, name, description, addedTenants);
  }

  public HashMap<String, Object> toMap() {
    HashMap<String, Object> result = new HashMap<>();
    result.put("id", identifier.value());
    result.put("name", name.value());
    result.put("description", description.value());
    result.put("assigned_tenants", assignedTenants.toMapList());
    result.put("enabled", enabled);
    return result;
  }

  public boolean hasAssignedTenants() {
    return assignedTenants != null && assignedTenants.exists();
  }

  public boolean exists() {
    return identifier != null && identifier.exists();
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Finds the admin tenant for this organization.
   *
   * @return the admin tenant
   * @throws AdminTenantNotFoundException if no admin tenant is found
   */
  public AssignedTenant findOrgTenant() {
    for (AssignedTenant tenant : assignedTenants()) {
      if ("ORGANIZER".equals(tenant.type())) {
        return tenant;
      }
    }
    throw new AdminTenantNotFoundException(
        "No admin tenant (type=ORGANIZER) found for organization: " + identifier.value());
  }

  public boolean hasAssignedTenant(TenantIdentifier tenantIdentifier) {
    return assignedTenants.contains(tenantIdentifier);
  }
}

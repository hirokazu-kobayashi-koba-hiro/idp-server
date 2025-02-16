package org.idp.sample.domain.model.organization;

import org.idp.sample.domain.model.tenant.Tenant;

import java.util.HashMap;

public class Organization {
  OrganizationIdentifier identifier;
  OrganizationName name;
  OrganizationDescription description;
  AssignedTenants assignedTenants;

  public Organization() {}

  public Organization(
      OrganizationIdentifier identifier,
      OrganizationName name,
      OrganizationDescription description,
      AssignedTenants assignedTenants) {
    this.identifier = identifier;
    this.name = name;
    this.description = description;
    this.assignedTenants = assignedTenants;
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

  public Organization assign(Tenant tenant) {
    AssignedTenants addedTenants = assignedTenants.add(tenant);
    return new Organization(identifier, name, description, addedTenants);
  }

  public Organization remove(Tenant tenant) {
    AssignedTenants removedTenants = assignedTenants.remove(tenant);
    return new Organization(identifier, name, description, removedTenants);
  }

  public HashMap<String, Object> toMap() {
    HashMap<String, Object> result = new HashMap<>();
    result.put("id", identifier.value());
    result.put("name", name.value());
    result.put("description", description.value());
    result.put("assigned_tenants", assignedTenants.toListMap());
    return result;
  }
}

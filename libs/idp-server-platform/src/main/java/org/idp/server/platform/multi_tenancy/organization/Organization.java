package org.idp.server.platform.multi_tenancy.organization;

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
      OrganizationDescription description) {
    this(identifier, name, description, new AssignedTenants());
  }

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
    return result;
  }

  public boolean hasAssignedTenants() {
    return assignedTenants != null && assignedTenants.exists();
  }
}

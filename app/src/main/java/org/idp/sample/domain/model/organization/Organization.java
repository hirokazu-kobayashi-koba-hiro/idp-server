package org.idp.sample.domain.model.organization;

import org.idp.sample.domain.model.tenant.Tenant;

public class Organization {
  OrganizationIdentifier identifier;
  OrganizationName name;
  OrganizationDescription description;
  AssignedTenants assignedTenants;

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

  public Organization assign(Tenant tenant) {
    AssignedTenants addedTenants = assignedTenants.add(tenant);
    return new Organization(identifier, name, description, addedTenants);
  }

  public Organization remove(Tenant tenant) {
    AssignedTenants removedTenants = assignedTenants.remove(tenant);
    return new Organization(identifier, name, description, removedTenants);
  }
}

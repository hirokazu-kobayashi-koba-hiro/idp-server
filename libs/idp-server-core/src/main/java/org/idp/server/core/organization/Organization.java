package org.idp.server.core.organization;

import java.util.HashMap;
import org.idp.server.core.basic.json.JsonReadable;
import org.idp.server.core.tenant.Tenant;

public class Organization implements JsonReadable {
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
    result.put("assigned_tenants", assignedTenants.toMapList());
    return result;
  }
}

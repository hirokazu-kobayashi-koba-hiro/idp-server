package org.idp.sample.domain.model.organization.initial;

import java.util.List;
import java.util.UUID;
import org.idp.sample.domain.model.organization.*;
import org.idp.sample.domain.model.tenant.Tenant;

public class OrganizationCreator {

  OrganizationName organizationName;
  Tenant tenant;

  public OrganizationCreator(OrganizationName organizationName, Tenant tenant) {
    this.organizationName = organizationName;
    this.tenant = tenant;
  }

  public Organization create() {
    OrganizationIdentifier identifier = new OrganizationIdentifier(UUID.randomUUID().toString());
    OrganizationDescription organizationDescription =
        new OrganizationDescription(tenant.name().value());
    AssignedTenants assignedTenants = new AssignedTenants(List.of(tenant));
    return new Organization(identifier, organizationName, organizationDescription, assignedTenants);
  }
}

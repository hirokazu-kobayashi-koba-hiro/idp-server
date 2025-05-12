package org.idp.server.control_plane.management.onboarding.io;

import org.idp.server.basic.json.JsonReadable;
import org.idp.server.core.multi_tenancy.organization.Organization;
import org.idp.server.core.multi_tenancy.organization.OrganizationDescription;
import org.idp.server.core.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.core.multi_tenancy.organization.OrganizationName;

public class OrganizationRegistrationRequest implements JsonReadable {

  String id;
  String name;
  String description;

  public OrganizationRegistrationRequest() {}

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public Organization toOrganization() {
    OrganizationIdentifier identifier = new OrganizationIdentifier(id);
    OrganizationName organizationName = new OrganizationName(name);
    OrganizationDescription organizationDescription = new OrganizationDescription(description);

    return new Organization(identifier, organizationName, organizationDescription);
  }
}

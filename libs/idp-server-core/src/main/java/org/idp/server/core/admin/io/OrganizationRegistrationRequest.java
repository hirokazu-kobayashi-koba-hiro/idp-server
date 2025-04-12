package org.idp.server.core.admin.io;

import org.idp.server.core.basic.json.JsonReadable;
import org.idp.server.core.organization.Organization;
import org.idp.server.core.organization.OrganizationDescription;
import org.idp.server.core.organization.OrganizationIdentifier;
import org.idp.server.core.organization.OrganizationName;

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

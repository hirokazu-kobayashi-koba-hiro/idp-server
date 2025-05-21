package org.idp.server.control_plane.management.onboarding.io;

import java.util.UUID;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationDescription;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationName;

public class OrganizationRegistrationRequest implements JsonReadable {

  String id;
  String name;
  String description;

  public OrganizationRegistrationRequest() {}

  public boolean hasId() {
    return id != null && !id.isEmpty();
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public Organization toOrganization() {
    String identifier = hasId() ? id : UUID.randomUUID().toString();
    OrganizationIdentifier organizationIdentifier = new OrganizationIdentifier(identifier);
    OrganizationName organizationName = new OrganizationName(name);
    OrganizationDescription organizationDescription = new OrganizationDescription(description);

    return new Organization(organizationIdentifier, organizationName, organizationDescription);
  }
}

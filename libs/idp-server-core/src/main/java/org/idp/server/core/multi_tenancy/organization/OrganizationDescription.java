package org.idp.server.core.multi_tenancy.organization;

public class OrganizationDescription {

  String value;

  public OrganizationDescription() {}

  public OrganizationDescription(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}

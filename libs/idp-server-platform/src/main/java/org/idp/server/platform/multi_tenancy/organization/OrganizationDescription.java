package org.idp.server.platform.multi_tenancy.organization;

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

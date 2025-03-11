package org.idp.server.adapters.springboot.domain.model.organization;

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

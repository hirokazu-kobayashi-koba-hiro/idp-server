package org.idp.server.domain.model.tenant;

public class PublicTenantDomain {
  String value;

  public PublicTenantDomain() {}

  public PublicTenantDomain(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}

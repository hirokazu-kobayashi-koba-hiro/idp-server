package org.idp.server.domain.model.tenant;

public class TenantName {

  String value;

  public TenantName() {}

  public TenantName(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}

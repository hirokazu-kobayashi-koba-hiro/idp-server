package org.idp.server.core.multi_tenancy.tenant;

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

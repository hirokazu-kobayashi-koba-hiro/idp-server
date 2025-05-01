package org.idp.server.core.multi_tenancy.tenant;

public class ServerDomain {
  String value;

  public ServerDomain() {}

  public ServerDomain(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}

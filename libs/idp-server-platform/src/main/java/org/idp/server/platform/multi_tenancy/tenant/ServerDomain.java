package org.idp.server.platform.multi_tenancy.tenant;

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

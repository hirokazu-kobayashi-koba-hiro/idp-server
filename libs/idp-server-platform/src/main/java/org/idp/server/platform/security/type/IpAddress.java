package org.idp.server.platform.security.type;

public class IpAddress {
  String value;

  public IpAddress() {}

  public IpAddress(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}

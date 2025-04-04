package org.idp.server.core.type.security;

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

package org.idp.server.core.oauth.client;

public class ClientName {
  String value;

  public ClientName() {}

  public ClientName(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }
}

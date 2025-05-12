package org.idp.server.basic.dependency.protocol;

public enum DefaultAuthorizationProvider {
  idp_server("idp-server");

  String value;

  DefaultAuthorizationProvider(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public AuthorizationProvider toAuthorizationProtocolProvider() {
    return new AuthorizationProvider(value);
  }
}

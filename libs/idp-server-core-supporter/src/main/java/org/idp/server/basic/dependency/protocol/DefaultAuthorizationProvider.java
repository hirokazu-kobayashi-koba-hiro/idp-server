package org.idp.server.basic.dependency.protocol;

public enum DefaultAuthorizationProvider {
  idp_server;

  public AuthorizationProvider toAuthorizationProtocolProvider() {
    return new AuthorizationProvider(name());
  }
}

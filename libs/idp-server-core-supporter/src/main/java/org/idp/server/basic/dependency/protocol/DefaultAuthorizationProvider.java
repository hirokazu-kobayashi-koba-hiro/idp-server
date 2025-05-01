package org.idp.server.basic.dependency.protocol;

public enum DefaultAuthorizationProvider {
  idp_server;

  public AuthorizationProtocolProvider toAuthorizationProtocolProvider() {
    return new AuthorizationProtocolProvider(name());
  }
}

package org.idp.server.core.basic.protcol;

public enum DefaultAuthorizationProvider {
  idp_server;

  public AuthorizationProtocolProvider toAuthorizationProtocolProvider() {
    return new AuthorizationProtocolProvider(name());
  }
}

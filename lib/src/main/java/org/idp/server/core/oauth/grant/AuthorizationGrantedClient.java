package org.idp.server.core.oauth.grant;

import org.idp.server.core.type.ClientId;

public class AuthorizationGrantedClient {

  ClientId clientId;

  public AuthorizationGrantedClient(ClientId clientId) {
    this.clientId = clientId;
  }

  public ClientId clientId() {
    return clientId;
  }

  public boolean isGranted(ClientId clientId) {
    return this.clientId.equals(clientId);
  }
}

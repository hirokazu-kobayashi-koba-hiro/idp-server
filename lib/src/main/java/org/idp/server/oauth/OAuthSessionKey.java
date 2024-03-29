package org.idp.server.oauth;

import java.io.Serializable;

public class OAuthSessionKey implements Serializable {
  String tokenIssuer;
  String clientId;

  public OAuthSessionKey(String tokenIssuer, String clientId) {
    this.tokenIssuer = tokenIssuer;
    this.clientId = clientId;
  }

  public String tokenIssuer() {
    return tokenIssuer;
  }

  public String clientId() {
    return clientId;
  }

  public String key() {
    return tokenIssuer + ":" + clientId;
  }
}

package org.idp.server.core.oauth;

import java.io.Serializable;
import java.util.Objects;

public class OAuthSessionKey implements Serializable {
  String tokenIssuer;
  String clientId;

  public OAuthSessionKey(String tokenIssuer, String clientId) {
    this.tokenIssuer = tokenIssuer;
    this.clientId = clientId;
  }

  public static OAuthSessionKey parse(String sessionKey) {
    String[] split = sessionKey.split(":");
    return new OAuthSessionKey(split[0], split[1]);
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

  public boolean exists() {
    return Objects.nonNull(tokenIssuer) && Objects.nonNull(clientId);
  }
}

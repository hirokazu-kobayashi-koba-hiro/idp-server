package org.idp.server.core.handler.userinfo.io;

import org.idp.server.core.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.type.mtls.ClientCert;
import org.idp.server.core.type.oauth.AccessTokenEntity;
import org.idp.server.core.type.oauth.TokenIssuer;

public class UserinfoRequest implements AuthorizationHeaderHandlerable {
  String authorizationHeaders;
  String issuer;
  String clientCert;

  public UserinfoRequest(String authorizationHeaders, String issuer) {
    this.authorizationHeaders = authorizationHeaders;
    this.issuer = issuer;
  }

  public String getAuthorizationHeaders() {
    return authorizationHeaders;
  }

  public String getIssuer() {
    return issuer;
  }

  public String getClientCert() {
    return clientCert;
  }

  public AccessTokenEntity toAccessToken() {
    return extractAccessToken(authorizationHeaders);
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(issuer);
  }

  public ClientCert toClientCert() {
    return new ClientCert(clientCert);
  }

  public UserinfoRequest setClientCert(String clientCert) {
    this.clientCert = clientCert;
    return this;
  }
}

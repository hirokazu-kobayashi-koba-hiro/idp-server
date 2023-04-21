package org.idp.server.handler.userinfo.io;

import org.idp.server.token.AuthorizationHeaderHandlerable;
import org.idp.server.type.oauth.AccessToken;
import org.idp.server.type.oauth.TokenIssuer;

public class UserinfoRequest implements AuthorizationHeaderHandlerable {
  String authorizationHeaders;
  String issuer;

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

  public AccessToken toAccessToken() {
    return extractAccessToken(authorizationHeaders);
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(issuer);
  }
}

package org.idp.server.handler.oauth.io;

import org.idp.server.oauth.request.OAuthLogoutParameters;
import org.idp.server.type.oauth.TokenIssuer;

import java.util.Map;

public class OAuthLogoutRequest {
  Map<String, String[]> params;
  String issuer;

  public OAuthLogoutRequest() {
    this.params = Map.of();
    this.issuer = "";
  }

  public OAuthLogoutRequest(Map<String, String[]> params, String issuer) {
    this.params = params;
    this.issuer = issuer;
  }

  public OAuthLogoutParameters toParameters() {
    return new OAuthLogoutParameters(params);
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(issuer);
  }
}

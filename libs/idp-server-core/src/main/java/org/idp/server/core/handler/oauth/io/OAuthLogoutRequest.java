package org.idp.server.core.handler.oauth.io;

import java.util.Map;
import org.idp.server.core.oauth.request.OAuthLogoutParameters;
import org.idp.server.core.type.oauth.TokenIssuer;

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

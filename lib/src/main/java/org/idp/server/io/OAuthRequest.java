package org.idp.server.io;

import java.util.Map;
import org.idp.server.core.type.OAuthRequestParameters;
import org.idp.server.core.type.TokenIssuer;

/** OAuthRequest */
public class OAuthRequest {

  Map<String, String[]> params;
  String issuer;

  public OAuthRequest() {
    this.params = Map.of();
    this.issuer = "";
  }

  public OAuthRequest(Map<String, String[]> params, String issuer) {
    this.params = params;
    this.issuer = issuer;
  }

  public Map<String, String[]> getParams() {
    return params;
  }

  public String getIssuer() {
    return issuer;
  }

  public OAuthRequestParameters toParameters() {
    return new OAuthRequestParameters(params);
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(issuer);
  }
}

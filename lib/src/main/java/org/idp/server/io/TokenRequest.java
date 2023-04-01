package org.idp.server.io;

import java.util.Map;
import org.idp.server.core.type.TokenIssuer;
import org.idp.server.core.type.TokenRequestParameters;

public class TokenRequest {
  Map<String, String[]> params;
  String issuer;

  public TokenRequest() {
    this.params = Map.of();
    this.issuer = "";
  }

  public TokenRequest(Map<String, String[]> params, String issuer) {
    this.params = params;
    this.issuer = issuer;
  }

  public Map<String, String[]> getParams() {
    return params;
  }

  public String getIssuer() {
    return issuer;
  }

  public TokenRequestParameters toParameters() {
    return new TokenRequestParameters(params);
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(issuer);
  }
}

package org.idp.server.handler.io;

import java.util.Map;
import org.idp.server.tokenintrospection.TokenIntrospectionRequestParameters;
import org.idp.server.type.oauth.TokenIssuer;

public class TokenIntrospectionRequest {
  Map<String, String[]> params;
  String issuer;

  public TokenIntrospectionRequest() {
    this.params = Map.of();
    this.issuer = "";
  }

  public TokenIntrospectionRequest(Map<String, String[]> params, String issuer) {
    this.params = params;
    this.issuer = issuer;
  }

  public Map<String, String[]> getParams() {
    return params;
  }

  public String getIssuer() {
    return issuer;
  }

  public TokenIntrospectionRequestParameters toParameters() {
    return new TokenIntrospectionRequestParameters(params);
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(issuer);
  }
}

package org.idp.server.core.handler.federation.io;

import java.util.Map;
import org.idp.server.core.federation.FederationCallbackParameters;
import org.idp.server.core.type.oauth.TokenIssuer;

public class FederationCallbackRequest {
  String issuer;
  Map<String, String[]> params;

  public FederationCallbackRequest() {}

  public FederationCallbackRequest(String issuer, Map<String, String[]> params) {
    this.issuer = issuer;
    this.params = params;
  }

  public TokenIssuer tokenIssuer() {
    return new TokenIssuer(issuer);
  }

  public FederationCallbackParameters parameters() {
    return new FederationCallbackParameters(params);
  }
}

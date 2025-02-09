package org.idp.server.handler.oauth.io;

import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.type.oauth.TokenIssuer;

public class OAuthViewDataRequest {
  String id;
  String tokenIssuer;

  public OAuthViewDataRequest(String id, String tokenIssuer) {
    this.id = id;
    this.tokenIssuer = tokenIssuer;
  }

  public AuthorizationRequestIdentifier toIdentifier() {
    return new AuthorizationRequestIdentifier(id);
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(tokenIssuer);
  }
}

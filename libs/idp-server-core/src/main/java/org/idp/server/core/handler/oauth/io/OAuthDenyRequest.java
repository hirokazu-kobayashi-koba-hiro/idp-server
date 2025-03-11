package org.idp.server.core.handler.oauth.io;

import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.type.extension.OAuthDenyReason;
import org.idp.server.core.type.oauth.TokenIssuer;

public class OAuthDenyRequest {
  String id;
  String tokenIssuer;
  OAuthDenyReason denyReason;

  public OAuthDenyRequest(String id, String tokenIssuer, OAuthDenyReason denyReason) {
    this.id = id;
    this.tokenIssuer = tokenIssuer;
    this.denyReason = denyReason;
  }

  public AuthorizationRequestIdentifier toIdentifier() {
    return new AuthorizationRequestIdentifier(id);
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(tokenIssuer);
  }

  public OAuthDenyReason denyReason() {
    return denyReason;
  }
}

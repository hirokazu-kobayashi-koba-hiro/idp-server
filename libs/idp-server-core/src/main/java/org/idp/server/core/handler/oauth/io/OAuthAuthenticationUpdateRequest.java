package org.idp.server.core.handler.oauth.io;

import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.type.oauth.TokenIssuer;

public class OAuthAuthenticationUpdateRequest {
  String id;
  String tokenIssuer;
  Authentication authentication;
  ;

  public OAuthAuthenticationUpdateRequest(
      String id, String tokenIssuer, Authentication authentication) {
    this.id = id;
    this.tokenIssuer = tokenIssuer;
    this.authentication = authentication;
  }

  public AuthorizationRequestIdentifier toIdentifier() {
    return new AuthorizationRequestIdentifier(id);
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(tokenIssuer);
  }

  public Authentication authentication() {
    return authentication;
  }
}

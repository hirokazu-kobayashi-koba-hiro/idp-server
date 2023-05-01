package org.idp.server.token.verifier;

import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.token.TokenRequestContext;

public class TokenRequestCodeGrantVerifier {

  TokenRequestCodeGrantBaseVerifier baseVerifier;
  TokenRequestPkceVerifier pkceVerifier;

  public TokenRequestCodeGrantVerifier() {
    this.baseVerifier = new TokenRequestCodeGrantBaseVerifier();
    this.pkceVerifier = new TokenRequestPkceVerifier();
  }

  public void verify(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant) {
    baseVerifier.verify(tokenRequestContext, authorizationRequest, authorizationCodeGrant);
    pkceVerifier.verify(tokenRequestContext, authorizationRequest);
  }
}

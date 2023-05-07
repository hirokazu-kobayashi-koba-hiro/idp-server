package org.idp.server.token.verifier;

import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.token.TokenRequestContext;

public class TokenRequestCodeGrantVerifier {

  TokenRequestCodeGrantBaseVerifier baseVerifier;
  TokenRequestPkceVerifier pkceVerifier;

  public TokenRequestCodeGrantVerifier(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant) {
    this.baseVerifier =
        new TokenRequestCodeGrantBaseVerifier(
            tokenRequestContext, authorizationRequest, authorizationCodeGrant);
    this.pkceVerifier = new TokenRequestPkceVerifier(tokenRequestContext, authorizationRequest);
  }

  public void verify() {
    baseVerifier.verify();
    pkceVerifier.verify();
  }
}

package org.idp.server.token.verifier;

import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.token.TokenRequestContext;

public class TokenRequestCodeGrantVerifier {

  public void verify(
      TokenRequestContext tokenRequestContext, AuthorizationCodeGrant authorizationCodeGrant) {}
}

package org.idp.server.oauth.verifier;

import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.verifier.base.AuthorizationRequestVerifier;
import org.idp.server.oauth.verifier.base.OAuthRequestBaseVerifier;

public class OAuth2RequestVerifier implements AuthorizationRequestVerifier {

  OAuthRequestBaseVerifier baseVerifier = new OAuthRequestBaseVerifier();

  @Override
  public void verify(OAuthRequestContext context) {
    throwIfInvalidRedirectUri(context);
    baseVerifier.verify(context);
  }

  void throwIfInvalidRedirectUri(OAuthRequestContext context) {}
}

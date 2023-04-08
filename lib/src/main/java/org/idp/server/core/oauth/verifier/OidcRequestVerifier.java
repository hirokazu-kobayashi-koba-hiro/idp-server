package org.idp.server.core.oauth.verifier;

import org.idp.server.core.oauth.OAuthRequestContext;

public class OidcRequestVerifier implements AuthorizationRequestVerifier {

  @Override
  public boolean shouldNotVerify(OAuthRequestContext oAuthRequestContext) {
    return !oAuthRequestContext.isOidcProfile();
  }

  @Override
  public void verify(OAuthRequestContext oAuthRequestContext) {
    throwIfInvalidRedirectUri(oAuthRequestContext);
  }

  void throwIfInvalidRedirectUri(OAuthRequestContext oAuthRequestContext) {

  }
}

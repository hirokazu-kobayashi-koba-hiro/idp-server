package org.idp.server.core.oauth.verifier;

import org.idp.server.core.oauth.OAuthRequestContext;

public class OidcRequestVerifier implements AuthorizationRequestVerifier {
  @Override
  public void verify(OAuthRequestContext oAuthRequestContext) {
    if (!oAuthRequestContext.isOidcProfile()) {
      return;
    }
    throwIfInvalidRedirectUri(oAuthRequestContext);
  }

  void throwIfInvalidRedirectUri(OAuthRequestContext oAuthRequestContext) {}
}

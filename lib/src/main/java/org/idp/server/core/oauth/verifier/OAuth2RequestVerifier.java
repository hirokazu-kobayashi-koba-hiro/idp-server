package org.idp.server.core.oauth.verifier;

import org.idp.server.core.oauth.OAuthRequestContext;

public class OAuth2RequestVerifier implements AuthorizationRequestVerifier {
  @Override
  public void verify(OAuthRequestContext oAuthRequestContext) {
    throwIfInvalidRedirectUri(oAuthRequestContext);
    throwIfUnSupportedResponseType(oAuthRequestContext);
  }

  void throwIfInvalidRedirectUri(OAuthRequestContext oAuthRequestContext) {}

  void throwIfUnSupportedResponseType(OAuthRequestContext oAuthRequestContext) {}
}

package org.idp.server.core.oauth.verifier;

import org.idp.server.core.oauth.OAuthRequestContext;

public class RequestObjectVerifier implements AuthorizationRequestVerifier {
  @Override
  public void verify(OAuthRequestContext oAuthRequestContext) {
    if (!oAuthRequestContext.isRequestParameterPattern()) {
      return;
    }
    throwIfExceed(oAuthRequestContext);
  }

  void throwIfExceed(OAuthRequestContext oAuthRequestContext) {}
}

package org.idp.server.core.oauth.verifier.extension;

import org.idp.server.core.oauth.OAuthRequestContext;

public class RequestObjectVerifier implements AuthorizationRequestExtensionVerifier {

  @Override
  public boolean shouldNotVerify(OAuthRequestContext context) {
    return !context.isRequestParameterPattern();
  }

  @Override
  public void verify(OAuthRequestContext oAuthRequestContext) {
    throwIfExceed(oAuthRequestContext);
  }

  void throwIfExceed(OAuthRequestContext oAuthRequestContext) {}
}

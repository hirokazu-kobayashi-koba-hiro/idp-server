package org.idp.server.oauth.verifier.extension;

import org.idp.server.oauth.OAuthRequestContext;

public class RequestObjectVerifier implements AuthorizationRequestExtensionVerifier {

  @Override
  public boolean shouldNotVerify(OAuthRequestContext context) {
    return !context.isRequestParameterPattern();
  }

  @Override
  public void verify(OAuthRequestContext oAuthRequestContext) {
    throwExceptionIfExceed(oAuthRequestContext);
  }

  void throwExceptionIfExceed(OAuthRequestContext oAuthRequestContext) {}
}

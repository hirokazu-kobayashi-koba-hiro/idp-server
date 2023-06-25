package org.idp.server.oauth.verifier.extension;

import org.idp.server.oauth.OAuthRequestContext;

public class JarmVerifier implements AuthorizationRequestExtensionVerifier {

  public boolean shouldNotVerify(OAuthRequestContext oAuthRequestContext) {
    return !oAuthRequestContext.responseMode().isJwtMode();
  }

  @Override
  public void verify(OAuthRequestContext oAuthRequestContext) {}
}

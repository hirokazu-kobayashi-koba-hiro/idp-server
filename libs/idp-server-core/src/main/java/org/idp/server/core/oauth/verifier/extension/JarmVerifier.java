package org.idp.server.core.oauth.verifier.extension;

import org.idp.server.core.oauth.OAuthRequestContext;

public class JarmVerifier implements AuthorizationRequestExtensionVerifier {

  public boolean shouldNotVerify(OAuthRequestContext oAuthRequestContext) {
    return !oAuthRequestContext.responseMode().isJwtMode();
  }

  @Override
  public void verify(OAuthRequestContext oAuthRequestContext) {}
}

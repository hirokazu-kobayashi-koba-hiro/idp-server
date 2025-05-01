package org.idp.server.core.oidc.verifier.extension;

import org.idp.server.core.oidc.OAuthRequestContext;

public class JarmVerifier implements AuthorizationRequestExtensionVerifier {

  public boolean shouldNotVerify(OAuthRequestContext oAuthRequestContext) {
    return !oAuthRequestContext.responseMode().isJwtMode();
  }

  @Override
  public void verify(OAuthRequestContext oAuthRequestContext) {}
}

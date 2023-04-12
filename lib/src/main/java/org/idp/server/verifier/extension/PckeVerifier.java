package org.idp.server.verifier.extension;

import org.idp.server.oauth.OAuthRequestContext;

public class PckeVerifier implements AuthorizationRequestExtensionVerifier {

  @Override
  public boolean shouldNotVerify(OAuthRequestContext context) {
    return true;
  }

  @Override
  public void verify(OAuthRequestContext context) {}
}

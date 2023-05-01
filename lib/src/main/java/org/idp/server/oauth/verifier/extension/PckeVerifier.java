package org.idp.server.oauth.verifier.extension;

import org.idp.server.oauth.OAuthRequestContext;

public class PckeVerifier implements AuthorizationRequestExtensionVerifier {

  @Override
  public boolean shouldNotVerify(OAuthRequestContext context) {
    return !context.isPckeRequest();
  }

  @Override
  public void verify(OAuthRequestContext context) {}
}

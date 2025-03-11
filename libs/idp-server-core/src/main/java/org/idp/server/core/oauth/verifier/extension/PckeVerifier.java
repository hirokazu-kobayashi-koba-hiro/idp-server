package org.idp.server.core.oauth.verifier.extension;

import org.idp.server.core.oauth.OAuthRequestContext;

public class PckeVerifier implements AuthorizationRequestExtensionVerifier {

  @Override
  public boolean shouldNotVerify(OAuthRequestContext context) {
    return !context.isPckeRequest();
  }

  @Override
  public void verify(OAuthRequestContext context) {}
}

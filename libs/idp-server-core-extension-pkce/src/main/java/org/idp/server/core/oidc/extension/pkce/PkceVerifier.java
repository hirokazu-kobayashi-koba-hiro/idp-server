package org.idp.server.core.oidc.extension.pkce;

import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.verifier.AuthorizationRequestExtensionVerifier;
import org.idp.server.platform.log.LoggerWrapper;

public class PkceVerifier implements AuthorizationRequestExtensionVerifier {

  LoggerWrapper log = LoggerWrapper.getLogger(PkceVerifier.class);

  @Override
  public boolean shouldNotVerify(OAuthRequestContext context) {
    return !context.isPckeRequest();
  }

  @Override
  public void verify(OAuthRequestContext context) {
    log.info("PKCE verification start");

    log.info("PKCE verification end");
  }
}

package org.idp.server.core.oidc.verifier;

import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.verifier.base.AuthorizationRequestVerifier;
import org.idp.server.core.oidc.verifier.base.OidcRequestBaseVerifier;

public class OidcRequestVerifier implements AuthorizationRequestVerifier {

  OidcRequestBaseVerifier baseVerifier = new OidcRequestBaseVerifier();

  @Override
  public void verify(OAuthRequestContext context) {
    baseVerifier.verify(context);
  }
}

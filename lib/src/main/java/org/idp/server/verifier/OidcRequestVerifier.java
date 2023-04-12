package org.idp.server.verifier;

import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.verifier.base.AuthorizationRequestVerifier;
import org.idp.server.verifier.base.OidcRequestBaseVerifier;

public class OidcRequestVerifier implements AuthorizationRequestVerifier {

  OidcRequestBaseVerifier baseVerifier = new OidcRequestBaseVerifier();

  @Override
  public void verify(OAuthRequestContext context) {
    baseVerifier.verify(context);
  }
}

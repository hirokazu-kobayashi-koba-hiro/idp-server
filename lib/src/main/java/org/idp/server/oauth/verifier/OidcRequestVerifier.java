package org.idp.server.oauth.verifier;

import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.verifier.base.AuthorizationRequestVerifier;
import org.idp.server.oauth.verifier.base.OidcRequestBaseVerifier;

public class OidcRequestVerifier implements AuthorizationRequestVerifier {

  OidcRequestBaseVerifier baseVerifier = new OidcRequestBaseVerifier();

  @Override
  public void verify(OAuthRequestContext context) {
    baseVerifier.verify(context);
  }
}

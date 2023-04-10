package org.idp.server.core.oauth.verifier;

import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.verifier.base.OidcRequestBaseVerifier;

public class OidcRequestVerifier implements AuthorizationRequestVerifier {

  OidcRequestBaseVerifier baseVerifier = new OidcRequestBaseVerifier();

  @Override
  public boolean shouldNotVerify(OAuthRequestContext oAuthRequestContext) {
    return !oAuthRequestContext.isOidcProfile();
  }

  @Override
  public void verify(OAuthRequestContext context) {
    baseVerifier.verify(context);
  }
}

package org.idp.server.verifier;

import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.verifier.base.AuthorizationRequestVerifier;

public class FapiBaselineVerifier implements AuthorizationRequestVerifier {

  @Override
  public void verify(OAuthRequestContext oAuthRequestContext) {}
}

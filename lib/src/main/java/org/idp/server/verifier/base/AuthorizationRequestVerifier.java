package org.idp.server.verifier.base;

import org.idp.server.oauth.OAuthRequestContext;

/** AuthorizationRequestVerifier */
public interface AuthorizationRequestVerifier {

  void verify(OAuthRequestContext oAuthRequestContext);
}

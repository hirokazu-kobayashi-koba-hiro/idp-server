package org.idp.server.oauth.verifier.base;

import org.idp.server.oauth.OAuthRequestContext;

/** AuthorizationRequestVerifier */
public interface AuthorizationRequestVerifier {

  void verify(OAuthRequestContext oAuthRequestContext);
}

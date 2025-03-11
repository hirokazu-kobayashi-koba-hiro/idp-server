package org.idp.server.core.oauth.verifier.base;

import org.idp.server.core.oauth.OAuthRequestContext;

/** AuthorizationRequestVerifier */
public interface AuthorizationRequestVerifier {

  void verify(OAuthRequestContext oAuthRequestContext);
}

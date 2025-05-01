package org.idp.server.core.oidc.verifier.base;

import org.idp.server.core.oidc.OAuthRequestContext;

/** AuthorizationRequestVerifier */
public interface AuthorizationRequestVerifier {

  void verify(OAuthRequestContext oAuthRequestContext);
}

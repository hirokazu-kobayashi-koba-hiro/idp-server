package org.idp.server.core.oauth.verifier;

import org.idp.server.core.oauth.OAuthRequestContext;

/** AuthorizationRequestVerifier */
public interface AuthorizationRequestVerifier {

  default boolean shouldNotVerify(OAuthRequestContext oAuthRequestContext) {
    return false;
  }
  void verify(OAuthRequestContext oAuthRequestContext);
}

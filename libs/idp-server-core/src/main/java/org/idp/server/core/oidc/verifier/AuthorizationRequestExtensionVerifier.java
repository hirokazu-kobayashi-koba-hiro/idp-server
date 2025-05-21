package org.idp.server.core.oidc.verifier;

import org.idp.server.core.oidc.OAuthRequestContext;

/** AuthorizationRequestExtensionVerifier */
public interface AuthorizationRequestExtensionVerifier {

  default boolean shouldNotVerify(OAuthRequestContext oAuthRequestContext) {
    return false;
  }

  void verify(OAuthRequestContext oAuthRequestContext);
}

package org.idp.server.core.oidc.verifier.extension;

import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.verifier.base.AuthorizationRequestVerifier;

/** AuthorizationRequestExtensionVerifier */
public interface AuthorizationRequestExtensionVerifier extends AuthorizationRequestVerifier {

  default boolean shouldNotVerify(OAuthRequestContext oAuthRequestContext) {
    return false;
  }
}

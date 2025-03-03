package org.idp.server.core.oauth.verifier.extension;

import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.verifier.base.AuthorizationRequestVerifier;

/** AuthorizationRequestExtensionVerifier */
public interface AuthorizationRequestExtensionVerifier extends AuthorizationRequestVerifier {

  default boolean shouldNotVerify(OAuthRequestContext oAuthRequestContext) {
    return false;
  }
}

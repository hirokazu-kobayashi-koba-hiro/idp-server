package org.idp.server.oauth.verifier.extension;

import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.verifier.base.AuthorizationRequestVerifier;

/** AuthorizationRequestExtensionVerifier */
public interface AuthorizationRequestExtensionVerifier extends AuthorizationRequestVerifier {

  default boolean shouldNotVerify(OAuthRequestContext oAuthRequestContext) {
    return false;
  }
}

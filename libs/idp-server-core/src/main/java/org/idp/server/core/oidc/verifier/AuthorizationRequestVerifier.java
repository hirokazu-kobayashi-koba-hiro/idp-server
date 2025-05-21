package org.idp.server.core.oidc.verifier;

import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.OAuthRequestContext;

/** AuthorizationRequestVerifier */
public interface AuthorizationRequestVerifier {

  AuthorizationProfile profile();

  void verify(OAuthRequestContext oAuthRequestContext);
}

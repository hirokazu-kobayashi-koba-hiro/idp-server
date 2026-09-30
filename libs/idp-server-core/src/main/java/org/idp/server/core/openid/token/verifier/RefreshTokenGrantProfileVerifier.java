/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.idp.server.core.openid.token.verifier;

import java.util.Map;
import java.util.Set;
import org.idp.server.core.openid.oauth.AuthorizationProfile;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.core.openid.token.plugin.RefreshTokenGrantVerifierPluginLoader;

/**
 * Applies the requirements of the profile the refreshed grant belongs to. Profiles without a
 * registered verifier (OAuth 2.0, OIDC) have nothing to add to {@link RefreshTokenVerifier}.
 */
public class RefreshTokenGrantProfileVerifier {

  Map<AuthorizationProfile, RefreshTokenGrantVerifierInterface> verifiers;

  public RefreshTokenGrantProfileVerifier() {
    this.verifiers = RefreshTokenGrantVerifierPluginLoader.load();
  }

  public void verify(
      TokenRequestContext tokenRequestContext,
      OAuthToken oAuthToken,
      ClientCredentials clientCredentials) {
    Set<String> scopes = oAuthToken.authorizationGrant().scopes().toStringSet();
    AuthorizationProfile profile =
        AuthorizationProfile.of(scopes, tokenRequestContext.serverConfiguration());
    RefreshTokenGrantVerifierInterface verifier = verifiers.get(profile);
    if (verifier == null) {
      return;
    }
    verifier.verify(tokenRequestContext, oAuthToken, clientCredentials);
  }
}

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

package org.idp.server.core.oidc.token.verifier;

import java.util.*;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.grant.AuthorizationCodeGrant;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.token.TokenRequestContext;
import org.idp.server.core.oidc.token.plugin.AuthorizationCodeGrantExtensionVerifierPluginLoader;
import org.idp.server.core.oidc.token.plugin.AuthorizationCodeGrantVerifierPluginLoader;
import org.idp.server.platform.exception.UnSupportedException;

public class AuthorizationCodeGrantVerifier {

  Map<AuthorizationProfile, AuthorizationCodeGrantVerifierInterface> baseVerifiers;
  List<AuthorizationCodeGrantExtensionVerifierInterface> extensionVerifiers;

  public AuthorizationCodeGrantVerifier() {
    this.baseVerifiers = new HashMap<>();
    baseVerifiers.put(AuthorizationProfile.OAUTH2, new AuthorizationCodeGrantOAuth2Verifier());
    baseVerifiers.put(AuthorizationProfile.OIDC, new AuthorizationCodeGrantOidcVerifier());
    baseVerifiers.put(AuthorizationProfile.UNDEFINED, new AuthorizationCodeGrantOAuth2Verifier());
    Map<AuthorizationProfile, AuthorizationCodeGrantVerifierInterface> loadedBaseVerifiers =
        AuthorizationCodeGrantVerifierPluginLoader.load();
    baseVerifiers.putAll(loadedBaseVerifiers);
    this.extensionVerifiers = AuthorizationCodeGrantExtensionVerifierPluginLoader.load();
  }

  public void verify(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant,
      ClientCredentials clientCredentials) {
    AuthorizationCodeGrantVerifierInterface baseVerifier =
        baseVerifiers.get(authorizationRequest.profile());

    if (Objects.isNull(baseVerifier)) {
      throw new UnSupportedException(
          String.format(
              "idp server does not supported profile (%s)", authorizationRequest.profile().name()));
    }

    baseVerifier.verify(
        tokenRequestContext, authorizationRequest, authorizationCodeGrant, clientCredentials);

    extensionVerifiers.forEach(
        extensionVerifier -> {
          if (extensionVerifier.shouldVerify(
              tokenRequestContext,
              authorizationRequest,
              authorizationCodeGrant,
              clientCredentials)) {
            extensionVerifier.verify(
                tokenRequestContext,
                authorizationRequest,
                authorizationCodeGrant,
                clientCredentials);
          }
        });
  }
}

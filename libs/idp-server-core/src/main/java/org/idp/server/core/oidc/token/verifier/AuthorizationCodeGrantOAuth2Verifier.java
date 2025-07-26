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

import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.grant.AuthorizationCodeGrant;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.token.TokenRequestContext;

public class AuthorizationCodeGrantOAuth2Verifier
    implements AuthorizationCodeGrantVerifierInterface {

  AuthorizationCodeGrantBaseVerifier baseVerifier = new AuthorizationCodeGrantBaseVerifier();

  @Override
  public AuthorizationProfile profile() {
    return AuthorizationProfile.OAUTH2;
  }

  @Override
  public void verify(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant,
      ClientCredentials clientCredentials) {
    baseVerifier.verify(
        tokenRequestContext, authorizationRequest, authorizationCodeGrant, clientCredentials);
  }
}

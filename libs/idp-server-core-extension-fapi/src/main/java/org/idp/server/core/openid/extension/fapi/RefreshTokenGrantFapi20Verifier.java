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
package org.idp.server.core.openid.extension.fapi;

import org.idp.server.core.openid.oauth.AuthorizationProfile;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.dpop.DPoPProof;
import org.idp.server.core.openid.oauth.dpop.DPoPProofVerifier;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.core.openid.token.verifier.RefreshTokenGrantVerifierInterface;

/**
 * FAPI 2.0 Security Profile requirements on a refresh token request. The client authentication
 * requirements apply to every request the client authenticates, so a refresh token request is held
 * to the same ones as the authorization code request.
 */
public class RefreshTokenGrantFapi20Verifier implements RefreshTokenGrantVerifierInterface {

  Fapi20ClientAuthenticationVerifier clientAuthenticationVerifier =
      new Fapi20ClientAuthenticationVerifier();

  @Override
  public AuthorizationProfile profile() {
    return AuthorizationProfile.FAPI_2_0;
  }

  @Override
  public void verify(
      TokenRequestContext tokenRequestContext,
      OAuthToken oAuthToken,
      ClientCredentials clientCredentials) {
    clientAuthenticationVerifier.verify(
        tokenRequestContext.serverConfiguration(),
        tokenRequestContext.clientConfiguration(),
        clientCredentials);
    throwExceptionIfInvalidDPoPSigningAlgorithm(tokenRequestContext);
  }

  /**
   * 5.4.1: the DPoP proof of a refresh token request is held to the same signing algorithms as the
   * one presented when the authorization code was exchanged.
   */
  void throwExceptionIfInvalidDPoPSigningAlgorithm(TokenRequestContext tokenRequestContext) {
    DPoPProof dpopProof = tokenRequestContext.dpopProof();
    if (dpopProof != null && dpopProof.exists()) {
      DPoPProofVerifier.verifyFapiSigningAlgorithm(dpopProof);
    }
  }
}

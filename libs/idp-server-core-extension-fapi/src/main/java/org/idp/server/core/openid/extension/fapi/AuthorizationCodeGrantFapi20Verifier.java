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

import org.idp.server.core.openid.grant_management.grant.AuthorizationCodeGrant;
import org.idp.server.core.openid.oauth.AuthorizationProfile;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.dpop.DPoPProof;
import org.idp.server.core.openid.oauth.dpop.DPoPProofVerifier;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.core.openid.token.verifier.AuthorizationCodeGrantBaseVerifier;
import org.idp.server.core.openid.token.verifier.AuthorizationCodeGrantVerifierInterface;

/**
 * FAPI 2.0 Security Profile Final - Token Endpoint requirements (Section 5.3.2.2).
 *
 * <p>FAPI 2.0 SP は以下を Token Endpoint で強制:
 *
 * <ul>
 *   <li>クライアント認証は mTLS または private_key_jwt のみ
 *   <li>Public Client 拒否
 *   <li>Sender-Constrained Token: mTLS または DPoP のいずれかでバインド
 * </ul>
 *
 * <p>{@code dpop_jkt} と DPoP proof JKT の一致検証は {@code AuthorizationCodeGrantService} 内で
 * 既に実施されているためここでは扱わない (RFC 9449 §10)。
 *
 * @see <a href="https://openid.net/specs/fapi-security-profile-2_0.html">FAPI 2.0 Security Profile
 *     Final</a>
 */
public class AuthorizationCodeGrantFapi20Verifier
    implements AuthorizationCodeGrantVerifierInterface {

  AuthorizationCodeGrantBaseVerifier baseVerifier = new AuthorizationCodeGrantBaseVerifier();
  Fapi20ClientAuthenticationVerifier clientAuthenticationVerifier =
      new Fapi20ClientAuthenticationVerifier();

  @Override
  public AuthorizationProfile profile() {
    return AuthorizationProfile.FAPI_2_0;
  }

  @Override
  public void verify(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant,
      ClientCredentials clientCredentials) {
    baseVerifier.verify(tokenRequestContext, authorizationRequest, authorizationCodeGrant);
    clientAuthenticationVerifier.verify(
        tokenRequestContext.serverConfiguration(),
        tokenRequestContext.clientConfiguration(),
        clientCredentials);
    throwExceptionIfNotSenderConstrained(tokenRequestContext, clientCredentials);
    throwExceptionIfInvalidDPoPSigningAlgorithm(tokenRequestContext);
  }

  /**
   * FAPI 2.0 §5.4: when the access token is DPoP-bound, the DPoP proof JWS signing algorithm is
   * restricted to the same strong asymmetric set as client assertions. Keeping the check in the
   * FAPI 2.0 profile verifier (rather than a scope flag in the generic grant service) mirrors how
   * the client-assertion algorithm restriction ({@link Fapi20ClientAuthenticationVerifier}) is
   * applied. {@link DPoPProofVerifier} owns the actual algorithm check and rejects a weak {@code
   * alg} (e.g. {@code RS256}) even when the tenant left {@code dpop_signing_alg_values_supported}
   * empty.
   */
  void throwExceptionIfInvalidDPoPSigningAlgorithm(TokenRequestContext tokenRequestContext) {
    DPoPProof dpopProof = tokenRequestContext.dpopProof();
    if (dpopProof != null && dpopProof.exists()) {
      DPoPProofVerifier.verifyFapiSigningAlgorithm(dpopProof);
    }
  }

  /**
   * FAPI 2.0 Section 5.3.2.1: アクセストークンは mTLS バインド or DPoP-bound のいずれかでなければならない。
   *
   * <p>判定は実際のリクエストで mTLS クライアント証明書または DPoP proof のいずれかが提示されているかで行う。 {@code dpop_jkt} の一致検証は {@code
   * AuthorizationCodeGrantService} 内で実施済み。
   */
  void throwExceptionIfNotSenderConstrained(
      TokenRequestContext tokenRequestContext, ClientCredentials clientCredentials) {
    boolean mtls = clientCredentials.hasClientCertification();
    boolean dpop =
        tokenRequestContext.dpopProof() != null && tokenRequestContext.dpopProof().exists();
    if (!mtls && !dpop) {
      throw new TokenBadRequestException(
          "invalid_request",
          "When FAPI 2.0 Security Profile, the access token MUST be sender-constrained via mTLS or DPoP.");
    }
  }
}

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
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
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
    throwExceptionIfWeakClientAuthentication(tokenRequestContext);
    throwExceptionIfNotSenderConstrained(tokenRequestContext, clientCredentials);
  }

  /**
   * FAPI 2.0 Section 5.3.3.4: client_secret_* / public client は禁止。tls_client_auth /
   * self_signed_tls_client_auth / private_key_jwt のみ許容。
   */
  void throwExceptionIfWeakClientAuthentication(TokenRequestContext tokenRequestContext) {
    ClientAuthenticationType type = tokenRequestContext.clientAuthenticationType();
    if (type.isClientSecretBasic() || type.isClientSecretPost() || type.isClientSecretJwt()) {
      throw new TokenBadRequestException(
          "unauthorized_client",
          "When FAPI 2.0 Security Profile, client_secret_* authentication is not allowed at the token endpoint.");
    }
    if (type.isNone()) {
      throw new TokenBadRequestException(
          "unauthorized_client",
          "When FAPI 2.0 Security Profile, public clients are not allowed at the token endpoint.");
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

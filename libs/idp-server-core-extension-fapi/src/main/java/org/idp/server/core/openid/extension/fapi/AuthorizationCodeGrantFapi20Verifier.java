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

import java.util.Set;
import org.idp.server.core.openid.grant_management.grant.AuthorizationCodeGrant;
import org.idp.server.core.openid.oauth.AuthorizationProfile;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAssertionJwt;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAuthenticationPublicKey;
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

  /** FAPI 2.0 §5.4: signing algorithms permitted on client assertions. */
  static final Set<String> ALLOWED_CLIENT_ASSERTION_ALGORITHMS =
      Set.of("PS256", "PS384", "PS512", "ES256", "ES384", "ES512", "EdDSA");

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
    throwExceptionIfInvalidSigningAlgorithmForClientAssertion(
        tokenRequestContext, clientCredentials);
    throwExceptionIfClientAssertionAudIsArray(tokenRequestContext, clientCredentials);
    throwExceptionIfClientAssertionAudIsNotIssuer(tokenRequestContext, clientCredentials);
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

  /**
   * FAPI 2.0 §5.4: client assertion JWS の署名アルゴリズムを PS256/PS384/PS512, ES256/ES384/ES512, EdDSA に制限。
   * RSASSA-PKCS1-v1_5 (RS256 等) や対称鍵 (HS256 等) は禁止。
   *
   * <p>合わせて FAPI 2.0 §5.4 由来の鍵長要件 (RSA ≥ 2048 bits, EC ≥ 224 bits) もここで強制する。
   */
  void throwExceptionIfInvalidSigningAlgorithmForClientAssertion(
      TokenRequestContext tokenRequestContext, ClientCredentials clientCredentials) {
    if (!tokenRequestContext.clientAuthenticationType().isPrivateKeyJwt()) {
      return;
    }

    ClientAssertionJwt clientAssertionJwt = clientCredentials.clientAssertionJwt();
    String algorithm = clientAssertionJwt.algorithm();

    if (!ALLOWED_CLIENT_ASSERTION_ALGORITHMS.contains(algorithm)) {
      throw new TokenBadRequestException(
          "invalid_client",
          String.format(
              "When FAPI 2.0 Security Profile (§5.4), client assertion signing algorithm must be one of %s. Current algorithm: %s",
              ALLOWED_CLIENT_ASSERTION_ALGORITHMS, algorithm));
    }

    ClientAuthenticationPublicKey publicKey = clientCredentials.clientAuthenticationPublicKey();
    int keySize = publicKey.size();

    if (algorithm.startsWith("PS") && keySize < 2048) {
      throw new TokenBadRequestException(
          "invalid_client",
          String.format(
              "When FAPI 2.0 Security Profile, RSA key size must be 2048 bits or larger. Current key size: %d bits",
              keySize));
    }
    if (algorithm.startsWith("ES") && keySize < 224) {
      throw new TokenBadRequestException(
          "invalid_client",
          String.format(
              "When FAPI 2.0 Security Profile, elliptic curve key size must be 224 bits or larger. Current key size: %d bits",
              keySize));
    }
  }

  /**
   * FAPI 2.0 §5.3.2.1-2.8: "shall only accept its issuer identifier value as a string in the aud
   * claim". client assertion の {@code aud} が配列で送られた場合は拒否する。
   */
  void throwExceptionIfClientAssertionAudIsArray(
      TokenRequestContext tokenRequestContext, ClientCredentials clientCredentials) {
    if (!tokenRequestContext.clientAuthenticationType().isPrivateKeyJwt()) {
      return;
    }
    if (clientCredentials.clientAssertionJwt().isAudArray()) {
      throw new TokenBadRequestException(
          "invalid_client",
          "When FAPI 2.0 Security Profile (§5.3.2.1-2.8), client assertion aud claim must be a string, not an array.");
    }
  }

  /**
   * FAPI 2.0 §5.3.2.1-2.8: "shall only accept its <b>issuer identifier value</b> (as defined in
   * [RFC8414]) as a string in the aud claim". token_endpoint URL や mTLS alias など issuer 以外を {@code
   * aud} に指定した client assertion は拒否する。
   */
  void throwExceptionIfClientAssertionAudIsNotIssuer(
      TokenRequestContext tokenRequestContext, ClientCredentials clientCredentials) {
    if (!tokenRequestContext.clientAuthenticationType().isPrivateKeyJwt()) {
      return;
    }
    Object rawAud = clientCredentials.clientAssertionJwt().getFromRawPayload("aud");
    if (!(rawAud instanceof String)) {
      return; // already rejected by other checks (missing / array)
    }
    String issuer = tokenRequestContext.serverConfiguration().tokenIssuer().value();
    if (!issuer.equals(rawAud)) {
      throw new TokenBadRequestException(
          "invalid_client",
          String.format(
              "When FAPI 2.0 Security Profile (§5.3.2.1-2.8), client assertion aud must be the AS issuer identifier (%s). Received: %s",
              issuer, rawAud));
    }
  }
}

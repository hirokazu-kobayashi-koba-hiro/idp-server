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
import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.RedirectUri;
import org.idp.server.core.openid.oauth.verifier.AuthorizationRequestVerifier;
import org.idp.server.core.openid.oauth.verifier.base.OAuthRequestBaseVerifier;
import org.idp.server.core.openid.oauth.verifier.base.OidcRequestBaseVerifier;

/**
 * FAPI 2.0 Security Profile Final - Authorization Server requirements (Section 5.3.2).
 *
 * <p>FAPI 2.0 SP は FAPI 1.0 Advanced からの大きな簡素化と厳格化を含む:
 *
 * <ul>
 *   <li>{@code response_type=code} のみ（Hybrid Flow 禁止）
 *   <li>PAR (RFC 9126) 必須 — 直接 Authorization Endpoint リクエストは拒否
 *   <li>PKCE S256 必須
 *   <li>Sender-Constrained Token: mTLS または DPoP 必須
 *   <li>Public Client 禁止
 *   <li>Confidential Client 認証は mTLS または private_key_jwt のみ
 *   <li>{@code iss} parameter (RFC 9207) を Authorization Response に含める（サーバ全体で対応済み）
 *   <li>Authorization Code Binding ({@code dpop_jkt}, RFC 9449 §10) — DPoP 使用時に必須
 * </ul>
 *
 * <p>Request Object は PAR で代替されるため、FAPI 1.0 Advanced のような JWS 署名済み Request Object の必須要件はない。
 *
 * @see <a href="https://openid.net/specs/fapi-security-profile-2_0.html">FAPI 2.0 Security Profile
 *     Final</a>
 */
public class FapiSecurity20Verifier implements AuthorizationRequestVerifier {

  OAuthRequestBaseVerifier oAuthRequestBaseVerifier = new OAuthRequestBaseVerifier();
  OidcRequestBaseVerifier oidcRequestBaseVerifier = new OidcRequestBaseVerifier();

  @Override
  public AuthorizationProfile profile() {
    return AuthorizationProfile.FAPI_2_0;
  }

  @Override
  public void verify(OAuthRequestContext context) {
    // OAuth 2.0 / OIDC base requirements
    if (context.isOidcRequest()) {
      oidcRequestBaseVerifier.verify(context);
    } else {
      oAuthRequestBaseVerifier.verify(context);
    }

    // FAPI 2.0 Section 5.3.2.2.3: PAR usage required
    throwExceptionIfNotPushedRequest(context);

    // FAPI 2.0 Section 5.3.2.1: response_type code only (Hybrid Flow prohibited)
    throwExceptionIfNotResponseTypeCode(context);

    // FAPI 2.0 Section 5.3.2.1.7: PKCE S256 required
    throwExceptionIfNotS256CodeChallengeMethod(context);

    // FAPI 2.0 Section 5.3.2.1.5b: nonce required when openid scope
    throwExceptionIfHasOpenidScopeAndNotContainsNonce(context);

    // FAPI 2.0 Section 5.3.2.1.5a: state required when no openid scope
    throwExceptionIfNotHasOpenidScopeAndNotContainsState(context);

    // FAPI 2.0 Section 5.3.2.1.6: redirect_uri https scheme required
    throwExceptionIfNotHttpsRedirectUri(context);

    // FAPI 2.0 Section 5.3.3.4: client authentication restriction
    // (mTLS or private_key_jwt only; client_secret_* prohibited)
    throwExceptionIfClientSecretPostOrClientSecretBasicOrClientSecretJwt(context);

    // FAPI 2.0 Section 5.3.3.1: public clients prohibited
    throwExceptionIfPublicClient(context);

    // FAPI 2.0 Section 5.3.2.1.5/5.3.2.1.6: sender-constrained access tokens
    // (mTLS or DPoP required)
    throwIfNotSenderConstrainedAccessToken(context);
  }

  /**
   * FAPI 2.0 Section 5.3.2.2.3: "Authorization Servers shall require Pushed Authorization Requests
   * for all authorization requests."
   *
   * <p>This check fires only at the authorization endpoint. At the PAR endpoint itself ({@code
   * isAtPushedEndpoint()} == true), the request is by definition being pushed and the check is
   * skipped.
   */
  void throwExceptionIfNotPushedRequest(OAuthRequestContext context) {
    if (context.isAtPushedEndpoint()) {
      return;
    }
    if (!context.isPushedRequest()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI 2.0 Security Profile, authorization requests MUST use Pushed Authorization Requests (PAR, RFC 9126). Direct authorization requests are not allowed.",
          context);
    }
  }

  /**
   * FAPI 2.0 Section 5.3.2.1: only the {@code code} response_type is allowed. Hybrid flows ({@code
   * code id_token}, etc.) are prohibited.
   */
  void throwExceptionIfNotResponseTypeCode(OAuthRequestContext context) {
    if (!context.responseType().isCode()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI 2.0 Security Profile, only response_type=code is allowed (Hybrid flows prohibited).",
          context);
    }
  }

  /**
   * FAPI 2.0 Section 5.3.2.1.7: "Authorization Servers shall require PKCE [RFC7636] with S256 as
   * the code challenge method."
   */
  void throwExceptionIfNotS256CodeChallengeMethod(OAuthRequestContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    if (!authorizationRequest.hasCodeChallenge()
        || !authorizationRequest.hasCodeChallengeMethod()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI 2.0 Security Profile, PKCE (RFC 7636) is required.",
          context);
    }
    if (!authorizationRequest.codeChallengeMethod().isS256()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI 2.0 Security Profile, code_challenge_method MUST be S256.",
          context);
    }
  }

  /** FAPI 2.0: nonce parameter required when openid scope is requested. */
  void throwExceptionIfHasOpenidScopeAndNotContainsNonce(OAuthRequestContext context) {
    if (!context.hasOpenidScope()) {
      return;
    }
    if (!context.authorizationRequest().hasNonce()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI 2.0 Security Profile and openid scope is requested, nonce parameter is required.",
          context);
    }
  }

  /** FAPI 2.0: state parameter required when openid scope is not requested. */
  void throwExceptionIfNotHasOpenidScopeAndNotContainsState(OAuthRequestContext context) {
    if (context.hasOpenidScope()) {
      return;
    }
    if (!context.authorizationRequest().hasState()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI 2.0 Security Profile without openid scope, state parameter is required.",
          context);
    }
  }

  /** FAPI 2.0 Section 5.3.2.1.6: redirect URIs MUST use the https scheme. */
  void throwExceptionIfNotHttpsRedirectUri(OAuthRequestContext context) {
    RedirectUri redirectUri = context.redirectUri();
    if (!redirectUri.isHttps()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "When FAPI 2.0 Security Profile, redirect URIs MUST use the https scheme (%s)",
              context.redirectUri().value()),
          context.tenant());
    }
  }

  /**
   * FAPI 2.0 Section 5.3.3.4: client authentication is restricted to {@code tls_client_auth},
   * {@code self_signed_tls_client_auth}, or {@code private_key_jwt}.
   */
  void throwExceptionIfClientSecretPostOrClientSecretBasicOrClientSecretJwt(
      OAuthRequestContext context) {
    ClientAuthenticationType clientAuthenticationType = context.clientAuthenticationType();
    if (clientAuthenticationType.isClientSecretBasic()
        || clientAuthenticationType.isClientSecretPost()
        || clientAuthenticationType.isClientSecretJwt()) {
      throw new OAuthRedirectableBadRequestException(
          "unauthorized_client",
          "When FAPI 2.0 Security Profile, client authentication MUST be one of mTLS or private_key_jwt. client_secret_* schemes are not allowed.",
          context);
    }
  }

  /** FAPI 2.0 Section 5.3.3.1: public clients are not supported. */
  void throwExceptionIfPublicClient(OAuthRequestContext context) {
    if (context.clientAuthenticationType().isNone()) {
      throw new OAuthRedirectableBadRequestException(
          "unauthorized_client",
          "When FAPI 2.0 Security Profile, public clients are not allowed.",
          context);
    }
  }

  /**
   * FAPI 2.0 Section 5.3.2.1: only sender-constrained access tokens are issued. The server SHALL
   * support mTLS (RFC 8705) or DPoP (RFC 9449).
   *
   * <p>本実装では、サーバ設定として mTLS バインドが有効、または DPoP の署名アルゴリズムがサポートされていれば sender-constrained
   * 化が可能とみなす。実際のバインドの有無は Token Endpoint で検証される （{@link AuthorizationCodeGrantFapi20Verifier}）。
   */
  void throwIfNotSenderConstrainedAccessToken(OAuthRequestContext context) {
    AuthorizationServerConfiguration serverConfiguration = context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    boolean mtlsCapable =
        serverConfiguration.isTlsClientCertificateBoundAccessTokens()
            && clientConfiguration.isTlsClientCertificateBoundAccessTokens();
    boolean dpopCapable = !serverConfiguration.dpopSigningAlgValuesSupported().isEmpty();
    if (!mtlsCapable && !dpopCapable) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI 2.0 Security Profile, sender-constrained access tokens are required (mTLS or DPoP must be enabled).",
          context);
    }
  }
}

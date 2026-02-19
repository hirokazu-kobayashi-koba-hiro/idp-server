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
 * FAPI 1.0 Baseline (Part 1) Section 5.2.2 - Authorization Server requirements.
 *
 * <p>Specification hierarchy:
 *
 * <pre>
 * OAuth 2.0 (RFC 6749)
 *   └─ OIDC Core 1.0
 *        └─ FAPI 1.0 Baseline (Part 1)  ← this class
 *             └─ FAPI 1.0 Advanced (Part 2)  → FapiAdvanceVerifier
 * </pre>
 *
 * <p>FAPI 1.0 Baseline Section 5.2.2 states: "In addition to the requirements in OAuth 2.0 and
 * OIDC, the authorization server SHALL ..."
 *
 * <p>This verifier delegates OAuth 2.0 / OIDC base validations to {@link OAuthRequestBaseVerifier}
 * and {@link OidcRequestBaseVerifier}, then applies Baseline-specific requirements on top.
 *
 * <p>Requirements verified by this class:
 *
 * <ul>
 *   <li>5.2.2-8: redirect_uri pre-registration required
 *   <li>5.2.2-9: redirect_uri parameter required
 *   <li>5.2.2-10: redirect_uri exact match
 *   <li>5.2.2-20: redirect_uri https scheme required
 *   <li>5.2.2-4: client authentication method restriction (mTLS / client_secret_jwt /
 *       private_key_jwt)
 *   <li>5.2.2-7: PKCE S256 required
 *   <li>5.2.2.2: nonce required when openid scope
 *   <li>5.2.2.3: state required when no openid scope
 * </ul>
 *
 * @see <a
 *     href="https://openid.net/specs/openid-financial-api-part-1-1_0.html#authorization-server">
 *     FAPI 1.0 Baseline Section 5.2.2</a>
 * @see FapiAdvanceVerifier
 */
public class FapiBaselineVerifier implements AuthorizationRequestVerifier {

  OAuthRequestBaseVerifier oAuthRequestBaseVerifier = new OAuthRequestBaseVerifier();
  OidcRequestBaseVerifier oidcRequestBaseVerifier = new OidcRequestBaseVerifier();

  public AuthorizationProfile profile() {
    return AuthorizationProfile.FAPI_BASELINE;
  }

  @Override
  public void verify(OAuthRequestContext context) {
    throwExceptionIfUnregisteredRedirectUri(context);
    throwExceptionIfNotContainsRedirectUri(context);
    throwExceptionUnMatchRedirectUri(context);
    throwExceptionIfNotHttpsRedirectUri(context);
    if (context.isOidcRequest()) {
      oidcRequestBaseVerifier.verify(context);
    } else {
      oAuthRequestBaseVerifier.verify(context);
    }
    throwExceptionIfClientSecretPostOrClientSecretBasic(context);
    throwExceptionIfNotS256CodeChallengeMethod(context);
    throwExceptionIfHasOpenidScopeAndNotContainsNonce(context);
    throwExceptionIfNotHasOpenidScopeAndNotContainsState(context);
  }

  /** shall require redirect URIs to be pre-registered; */
  void throwExceptionIfUnregisteredRedirectUri(OAuthRequestContext context) {
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    if (!clientConfiguration.hasRedirectUri()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          "When FAPI Baseline profile, shall require redirect URIs to be pre-registered",
          context.tenant());
    }
  }

  /** shall require the redirect_uri in the authorization request; */
  void throwExceptionIfNotContainsRedirectUri(OAuthRequestContext context) {
    if (!context.hasRedirectUriInRequest()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          "When FAPI Baseline profile, shall require the redirect_uri in the authorization request",
          context.tenant());
    }
  }

  /**
   * shall require the value of redirect_uri to exactly match one of the pre-registered redirect
   * URIs;
   */
  void throwExceptionUnMatchRedirectUri(OAuthRequestContext context) {
    if (!context.isRegisteredRedirectUri()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "When FAPI Baseline profile, shall require the value of redirect_uri to exactly match one of the pre-registered redirect URIs (%s)",
              context.redirectUri().value()),
          context.tenant());
    }
  }

  /** shall require redirect URIs to use the https scheme; */
  void throwExceptionIfNotHttpsRedirectUri(OAuthRequestContext context) {
    RedirectUri redirectUri = context.redirectUri();
    if (!redirectUri.isHttps()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "When FAPI Baseline profile, shall shall require redirect URIs to use the https scheme (%s)",
              context.redirectUri().value()),
          context.tenant());
    }
  }

  /**
   * shall authenticate the confidential client using one of the following methods: Mutual TLS for
   * OAuth Client Authentication as specified in Section 2 of MTLS, or client_secret_jwt or
   * private_key_jwt as specified in Section 9 of OIDC;
   */
  void throwExceptionIfClientSecretPostOrClientSecretBasic(OAuthRequestContext context) {
    ClientAuthenticationType clientAuthenticationType = context.clientAuthenticationType();
    if (clientAuthenticationType.isClientSecretBasic()) {
      throw new OAuthRedirectableBadRequestException(
          "unauthorized_client",
          "When FAPI Baseline profile, client_secret_basic MUST not used",
          context);
    }
    if (clientAuthenticationType.isClientSecretPost()) {
      throw new OAuthRedirectableBadRequestException(
          "unauthorized_client",
          "When FAPI Baseline profile, client_secret_post MUST not used",
          context);
    }
  }

  /** shall require RFC7636 with S256 as the code challenge method; */
  void throwExceptionIfNotS256CodeChallengeMethod(OAuthRequestContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    if (!authorizationRequest.hasCodeChallenge()
        || !authorizationRequest.hasCodeChallengeMethod()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Baseline profile, authorization request must contains code_challenge and code_challenge_method(S256).",
          context);
    }
    if (!authorizationRequest.codeChallengeMethod().isS256()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Baseline profile, shall require RFC7636 with S256 as the code challenge method.",
          context);
    }
  }

  /**
   * 5.2.2.2. Client requesting openid scope
   *
   * <p>If the client requests the openid scope, the authorization server shall require the nonce
   * parameter defined in Section 3.1.2.1 of OIDC in the authentication request.
   */
  void throwExceptionIfHasOpenidScopeAndNotContainsNonce(OAuthRequestContext context) {
    if (!context.hasOpenidScope()) {
      return;
    }
    if (!context.authorizationRequest().hasNonce()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Baseline profile, shall require the nonce parameter defined in Section 3.1.2.1 of OIDC in the authentication request.",
          context);
    }
  }

  /**
   * 5.2.2.3. Clients not requesting openid scope
   *
   * <p>If the client does not requests the openid scope, the authorization server shall require the
   * state parameter defined in Section 4.1.1 of RFC6749.
   */
  void throwExceptionIfNotHasOpenidScopeAndNotContainsState(OAuthRequestContext context) {
    if (context.hasOpenidScope()) {
      return;
    }
    if (!context.authorizationRequest().hasState()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Baseline profile, shall require the state parameter defined in Section 4.1.1 of RFC 6749.",
          context);
    }
  }
}

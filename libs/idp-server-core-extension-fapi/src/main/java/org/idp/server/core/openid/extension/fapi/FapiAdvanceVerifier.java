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

import java.util.Date;
import java.util.List;
import java.util.Map;
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
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.jose.JsonWebTokenClaims;

/**
 * FAPI 1.0 Advanced (Part 2) Section 5.2.2 - Authorization Server requirements.
 *
 * <p>Specification hierarchy:
 *
 * <pre>
 * OAuth 2.0 (RFC 6749)
 *   └─ OIDC Core 1.0
 *        └─ FAPI 1.0 Baseline (Part 1)  → FapiBaselineVerifier
 *             └─ FAPI 1.0 Advanced (Part 2)  ← this class
 * </pre>
 *
 * <p>FAPI 1.0 Advanced Section 5.2.2 states: "In addition to the provisions in Section 5.2.2 of
 * [FAPI1-BASE], the authorization server SHALL ..."
 *
 * <p>This means Advanced inherits ALL Baseline requirements, with some replacements. This class
 * does NOT delegate to {@link FapiBaselineVerifier} to allow explicit control over which Baseline
 * requirements are inherited versus replaced. Each requirement is explicitly listed in {@link
 * #verify(OAuthRequestContext)} with comments indicating its origin.
 *
 * <p><b>Inherited from Baseline (unchanged):</b>
 *
 * <ul>
 *   <li>5.2.2-8: redirect_uri pre-registration → covered by {@link OidcRequestBaseVerifier}
 *   <li>5.2.2-9: redirect_uri parameter required → covered by {@link OidcRequestBaseVerifier}
 *   <li>5.2.2-10: redirect_uri exact match → covered by {@link OidcRequestBaseVerifier}
 *   <li>5.2.2-20: redirect_uri https scheme required
 *   <li>5.2.2.2: nonce required when openid scope
 *   <li>5.2.2.3: state required when no openid scope
 * </ul>
 *
 * <p><b>Replaced from Baseline:</b>
 *
 * <ul>
 *   <li>5.2.2-4 → 5.2.2-14: client auth restriction (adds client_secret_jwt prohibition)
 *   <li>5.2.2-7 → 5.2.2-18: PKCE S256 (always required → PAR only)
 * </ul>
 *
 * <p><b>Advanced-specific:</b>
 *
 * <ul>
 *   <li>5.2.2-1: signed JWT request object required
 *   <li>5.2.2-2: response_type restriction (code id_token or code+jwt)
 *   <li>5.2.2-5/6: sender-constrained access tokens via mTLS
 *   <li>5.2.2-13: request object exp-nbf <= 60 minutes
 *   <li>5.2.2-15: request object aud validation
 *   <li>5.2.2-16: public clients prohibited
 *   <li>5.2.2-17: request object nbf not older than 60 minutes
 *   <li>8.6: signing algorithm restrictions (PS256/ES256 only)
 * </ul>
 *
 * @see <a
 *     href="https://openid.net/specs/openid-financial-api-part-2-1_0.html#authorization-server">
 *     FAPI 1.0 Advanced Final Section 5.2.2</a>
 * @see FapiBaselineVerifier
 */
public class FapiAdvanceVerifier implements AuthorizationRequestVerifier {

  private static final long SIXTY_MINUTES_IN_MILLIS = 60 * 60 * 1000L;

  OAuthRequestBaseVerifier oAuthRequestBaseVerifier = new OAuthRequestBaseVerifier();
  OidcRequestBaseVerifier oidcRequestBaseVerifier = new OidcRequestBaseVerifier();

  public AuthorizationProfile profile() {
    return AuthorizationProfile.FAPI_ADVANCE;
  }

  /**
   * Verifies authorization server requirements defined in FAPI 1.0 Advanced Final Section 5.2.2.
   *
   * <p>This method is invoked during authorization request verification. Requests arrive via two
   * paths:
   *
   * <ol>
   *   <li><b>Direct request</b>: via {@code request} parameter (JWT) or {@code request_uri}
   *       parameter. The JoseContext contains the Request Object signature and claims, so all
   *       validations are executed.
   *   <li><b>PAR-based request (RFC 9126)</b>: parameters are submitted to the PAR endpoint in
   *       advance, and the authorization endpoint is accessed with the issued {@code request_uri}
   *       (urn:ietf:params:oauth:request_uri:...). Request Object validation (signature, exp, nbf,
   *       aud) is already completed at PAR endpoint acceptance. Since the authorization endpoint
   *       restores stored parameters, the JoseContext is empty. Therefore, Request Object specific
   *       claim validations (exp, nbf, aud, signature) are skipped.
   * </ol>
   *
   * @see <a
   *     href="https://openid.net/specs/openid-financial-api-part-2-1_0.html#authorization-server">
   *     FAPI 1.0 Advanced Final Section 5.2.2</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9126">RFC 9126 - OAuth 2.0 Pushed Authorization
   *     Requests</a>
   */
  @Override
  public void verify(OAuthRequestContext context) {
    throwIfExceptionInvalidConfig(context);

    // --- OAuth 2.0 / OIDC base requirements ---
    if (context.isOidcRequest()) {
      oidcRequestBaseVerifier.verify(context);
    } else {
      oAuthRequestBaseVerifier.verify(context);
    }

    // --- Inherited from Baseline (unchanged) ---
    // 5.2.2-8/9/10: redirect_uri pre-registration, required, exact match
    //   → covered by OidcRequestBaseVerifier / OAuthRequestBaseVerifier above
    // 5.2.2-20: redirect_uri https scheme required
    throwExceptionIfNotHttpsRedirectUri(context);
    // 5.2.2.2: nonce required when openid scope
    throwExceptionIfHasOpenidScopeAndNotContainsNonce(context);
    // 5.2.2.3: state required when no openid scope
    throwExceptionIfNotHasOpenidScopeAndNotContainsState(context);

    // --- Replaced from Baseline ---
    // 5.2.2-14 (replaces Baseline 5.2.2-4): client auth restriction (+client_secret_jwt)
    throwExceptionIfClientSecretPostOrClientSecretBasicOrClientSecretJwt(context);
    // 5.2.2-18 (replaces Baseline 5.2.2-7): PKCE S256 required only for PAR
    if (context.isPushedRequest()) {
      throwExceptionIfNotS256CodeChallengeMethodForPAR(context);
    }

    // --- Advanced-specific requirements ---
    // 5.2.2-1: signed JWT request object required
    throwExceptionIfNotRequestParameterPattern(context);
    // 8.6: signing algorithm restrictions (PS256/ES256 only, skipped for PAR)
    if (!context.isPushedRequest()) {
      throwExceptionIfInvalidSigningAlgorithm(context);
    }
    // 5.2.2-2: response_type restriction (code id_token or code+jwt)
    throwExceptionIfInvalidResponseTypeAndResponseMode(context);
    // 5.2.2-5/6: sender-constrained access tokens via mTLS
    throwIfNotSenderConstrainedAccessToken(context);
    // 5.2.2-16: public clients prohibited
    throwExceptionIfPublicClient(context);
    // 5.2.2-13/15/17: Request Object claims validation (skipped for PAR)
    if (!context.isPushedRequest()) {
      throwExceptionIfNotContainExpAndNbfAndExp60minutesLongerThanNbf(context);
      throwExceptionIfNotContainsAud(context);
      throwExceptionIfNotContainNbfAnd60minutesLongerThan(context);
    }
  }

  void throwIfExceptionInvalidConfig(OAuthRequestContext context) {
    AuthorizationServerConfiguration authorizationServerConfiguration =
        context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    if (context.isJwtMode()) {
      if (!clientConfiguration.hasAuthorizationSignedResponseAlg()) {
        throw new OAuthBadRequestException(
            "unauthorized_client",
            "When FAPI Advance profile and jarm mode, client config must have authorization_signed_response_alg",
            context.tenant());
      }
      if (!authorizationServerConfiguration.hasKey(
          clientConfiguration.authorizationSignedResponseAlg())) {
        throw new OAuthBadRequestException(
            "unauthorized_client",
            "When FAPI Advance profile and jarm mode, server jwks must have client authorization_signed_response_alg",
            context.tenant());
      }
    }
  }

  /**
   * FAPI 1.0 Advanced Final Section 5.2.2 clause 1: shall require a JWS signed JWT request object
   * passed by value with the request parameter or by reference with the request_uri parameter.
   *
   * <p>Accepted request patterns:
   *
   * <ul>
   *   <li>{@code request} parameter (REQUEST_OBJECT): JWS-signed JWT Request Object
   *   <li>{@code request_uri} parameter (REQUEST_URI): reference to a JWS-signed JWT Request Object
   *   <li>PAR {@code request_uri} (PUSHED_REQUEST_URI): Pushed Authorization Request per RFC 9126.
   *       Request Object signature validation is already completed at the PAR endpoint, so the
   *       empty JoseContext at the authorization endpoint is acceptable.
   * </ul>
   *
   * <p>Request Objects with alg:none (unsigned) are rejected. However, for PAR-based requests the
   * unsigned check is skipped because the JoseContext is not preserved after PAR storage (already
   * validated at the PAR endpoint).
   *
   * @see <a
   *     href="https://openid.net/specs/openid-financial-api-part-2-1_0.html#authorization-server">
   *     FAPI 1.0 Advanced Final Section 5.2.2</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9126#section-2">RFC 9126 Section 2</a>
   */
  void throwExceptionIfNotRequestParameterPattern(OAuthRequestContext context) {
    if (!context.isRequestParameterPattern() && !context.isPushedRequest()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Advance profile, shall require a JWS signed JWT request object passed by value with the request parameter or by reference with the request_uri parameter",
          context);
    }
    if (!context.isPushedRequest() && context.isUnsignedRequestObject()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object",
          "When FAPI Advance profile, request object must be signed with a JWS algorithm, alg:none is not allowed",
          context);
    }
  }

  /**
   * shall require the response_type value code id_token, or the response_type value code in
   * conjunction with the response_mode value jwt;
   */
  void throwExceptionIfInvalidResponseTypeAndResponseMode(OAuthRequestContext context) {
    if (context.responseType().isCodeIdToken()) {
      return;
    }
    if (context.responseType().isCode() && context.responseMode().isJwt()) {
      return;
    }
    throw new OAuthRedirectableBadRequestException(
        "invalid_request",
        "When FAPI Advance profile, shall require the response_type value code id_token, or the response_type value code in conjunction with the response_mode value jwt",
        context);
  }

  /**
   * shall only issue sender-constrained access tokens;
   *
   * <p>shall support MTLS as mechanism for constraining the legitimate senders of access tokens;
   */
  void throwIfNotSenderConstrainedAccessToken(OAuthRequestContext context) {
    AuthorizationServerConfiguration authorizationServerConfiguration =
        context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    if (!authorizationServerConfiguration.isTlsClientCertificateBoundAccessTokens()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Advance profile, shall only issue sender-constrained access tokens, but server tls_client_certificate_bound_access_tokens is false",
          context);
    }
    if (!clientConfiguration.isTlsClientCertificateBoundAccessTokens()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Advance profile, shall only issue sender-constrained access tokens, but client tls_client_certificate_bound_access_tokens is false",
          context);
    }
  }

  /**
   * shall require the request object to contain an exp claim that has a lifetime of no longer than
   * 60 minutes after the nbf claim;
   */
  void throwExceptionIfNotContainExpAndNbfAndExp60minutesLongerThanNbf(
      OAuthRequestContext context) {
    JoseContext joseContext = context.joseContext();
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasExp()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object",
          "When FAPI Advance profile, shall require the request object to contain an exp claim",
          context);
    }
    if (!claims.hasNbf()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object",
          "When FAPI Advance profile, shall require the request object to contain an nbf claim",
          context);
    }
    Date exp = claims.getExp();
    Date nbf = claims.getNbf();
    if (exp.getTime() - nbf.getTime() > SIXTY_MINUTES_IN_MILLIS) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object",
          "When FAPI Advance profile, shall require the request object to contain an exp claim that has a lifetime of no longer than 60 minutes after the nbf claim",
          context);
    }
  }

  /**
   * FAPI 1.0 Advanced Section 5.2.2-15: shall require the aud claim in the request object to be, or
   * to be an array containing, the OP's Issuer Identifier URL.
   *
   * <p>Also accepts:
   *
   * <ul>
   *   <li>PAR endpoint URL (RFC 9126 - when Request Object is sent to PAR endpoint)
   *   <li>mTLS alias of PAR endpoint (RFC 8705 Section 5)
   * </ul>
   */
  void throwExceptionIfNotContainsAud(OAuthRequestContext context) {
    JoseContext joseContext = context.joseContext();
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasAud()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object",
          "When FAPI Advance profile, shall require the request object to contain an aud claim",
          context);
    }
    List<String> aud = claims.getAud();
    AuthorizationServerConfiguration serverConfig = context.serverConfiguration();
    // FAPI 1.0 Advanced Section 5.2.2-15: Issuer Identifier
    if (aud.contains(context.tokenIssuer().value())) {
      return;
    }
    // RFC 9126: PAR endpoint URL
    if (serverConfig.hasPushedAuthorizationRequestEndpoint()
        && aud.contains(serverConfig.pushedAuthorizationRequestEndpoint())) {
      return;
    }
    // RFC 8705 Section 5: mTLS alias of PAR endpoint
    if (serverConfig.hasMtlsEndpointAliases()) {
      Map<String, String> aliases = serverConfig.mtlsEndpointAliases();
      if (aud.contains(aliases.get("pushed_authorization_request_endpoint"))) {
        return;
      }
    }
    throw new OAuthRedirectableBadRequestException(
        "invalid_request_object",
        String.format(
            "When FAPI Advance profile, shall require the aud claim in the request object to be, or to be an array containing, the OP's Issuer Identifier URL or pushed_authorization_request_endpoint (%s)",
            String.join(" ", aud)),
        context);
  }

  /**
   * shall authenticate the confidential client using one of the following methods (this overrides
   * FAPI Security Profile 1.0 - Part 1: Baseline clause 5.2.2-4): tls_client_auth or
   * self_signed_tls_client_auth as specified in section 2 of MTLS, or private_key_jwt as specified
   * in section 9 of OIDC;
   */
  void throwExceptionIfClientSecretPostOrClientSecretBasicOrClientSecretJwt(
      OAuthRequestContext context) {
    ClientAuthenticationType clientAuthenticationType = context.clientAuthenticationType();
    if (clientAuthenticationType.isClientSecretBasic()) {
      throw new OAuthRedirectableBadRequestException(
          "unauthorized_client",
          "When FAPI Advance profile, client_secret_basic MUST not used",
          context);
    }
    if (clientAuthenticationType.isClientSecretPost()) {
      throw new OAuthRedirectableBadRequestException(
          "unauthorized_client",
          "When FAPI Advance profile, client_secret_post MUST not used",
          context);
    }
    if (clientAuthenticationType.isClientSecretJwt()) {
      throw new OAuthRedirectableBadRequestException(
          "unauthorized_client",
          "When FAPI Advance profile, client_secret_jwt MUST not used",
          context);
    }
  }

  /**
   * FAPI 1.0 Advanced Final Section 5.2.2-18:
   *
   * <p>"shall require PAR requests, if supported, to use PKCE (RFC7636) with S256 as the code
   * challenge method"
   *
   * <p>This replaces the Baseline 5.2.2-7 PKCE requirement. In FAPI Advanced, PKCE is only required
   * when PAR is used. For non-PAR requests (e.g., Hybrid Flow with code id_token), PKCE is not
   * required because the ID Token's c_hash provides authorization code integrity protection.
   */
  void throwExceptionIfNotS256CodeChallengeMethodForPAR(OAuthRequestContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    if (!authorizationRequest.hasCodeChallenge()
        || !authorizationRequest.hasCodeChallengeMethod()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Advance profile with PAR, shall require PKCE (RFC7636) with S256 as the code challenge method",
          context);
    }
    if (!authorizationRequest.codeChallengeMethod().isS256()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Advance profile with PAR, shall require S256 as the code challenge method",
          context);
    }
  }

  /**
   * Inherited from FAPI 1.0 Baseline 5.2.2-20: shall require redirect URIs to use the https scheme.
   */
  void throwExceptionIfNotHttpsRedirectUri(OAuthRequestContext context) {
    RedirectUri redirectUri = context.redirectUri();
    if (!redirectUri.isHttps()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "When FAPI Advance profile, shall require redirect URIs to use the https scheme (%s)",
              context.redirectUri().value()),
          context.tenant());
    }
  }

  /**
   * Inherited from FAPI 1.0 Baseline 5.2.2.2: If the client requests the openid scope, the
   * authorization server shall require the nonce parameter defined in Section 3.1.2.1 of OIDC in
   * the authentication request.
   */
  void throwExceptionIfHasOpenidScopeAndNotContainsNonce(OAuthRequestContext context) {
    if (!context.hasOpenidScope()) {
      return;
    }
    if (!context.authorizationRequest().hasNonce()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Advance profile, shall require the nonce parameter defined in Section 3.1.2.1 of OIDC in the authentication request (inherited from FAPI Baseline 5.2.2.2).",
          context);
    }
  }

  /**
   * Inherited from FAPI 1.0 Baseline 5.2.2.3: If the client does not request the openid scope, the
   * authorization server shall require the state parameter defined in Section 4.1.1 of RFC 6749.
   */
  void throwExceptionIfNotHasOpenidScopeAndNotContainsState(OAuthRequestContext context) {
    if (context.hasOpenidScope()) {
      return;
    }
    if (!context.authorizationRequest().hasState()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Advance profile, shall require the state parameter defined in Section 4.1.1 of RFC 6749 (inherited from FAPI Baseline 5.2.2.3).",
          context);
    }
  }

  /** 5.2.2-16: shall not support public clients. */
  void throwExceptionIfPublicClient(OAuthRequestContext context) {
    ClientAuthenticationType clientAuthenticationType = context.clientAuthenticationType();
    if (clientAuthenticationType.isNone()) {
      throw new OAuthRedirectableBadRequestException(
          "unauthorized_client",
          "When FAPI Advance profile, shall not support public clients",
          context);
    }
  }

  /**
   * FAPI 1.0 Advanced Section 8.6: Algorithm restrictions for JWS.
   *
   * <p>shall use PS256 or ES256 algorithms; shall not use algorithms that use RSASSA-PKCS1-v1_5
   * (e.g. RS256); shall not use none.
   */
  void throwExceptionIfInvalidSigningAlgorithm(OAuthRequestContext context) {
    JoseContext joseContext = context.joseContext();
    if (!joseContext.hasJsonWebSignature()) {
      return;
    }
    String algorithm = joseContext.jsonWebSignature().algorithm();
    if (!"PS256".equals(algorithm) && !"ES256".equals(algorithm)) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object",
          String.format(
              "When FAPI Advance profile, request object signing algorithm must be PS256 or ES256 (Section 8.6). Current algorithm: %s",
              algorithm),
          context);
    }
  }

  /**
   * shall require the request object to contain an nbf claim that is no longer than 60 minutes in
   * the past; and
   */
  void throwExceptionIfNotContainNbfAnd60minutesLongerThan(OAuthRequestContext context) {
    JoseContext joseContext = context.joseContext();
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasNbf()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object",
          "When FAPI Advance profile, shall require the request object to contain an nbf claim",
          context);
    }
    Date now = new Date();
    Date nbf = claims.getNbf();
    if (now.getTime() - nbf.getTime() > SIXTY_MINUTES_IN_MILLIS) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object",
          "When FAPI Advance profile, shall require the request object to contain an nbf claim that is no longer than 60 minutes in the past",
          context);
    }
  }
}

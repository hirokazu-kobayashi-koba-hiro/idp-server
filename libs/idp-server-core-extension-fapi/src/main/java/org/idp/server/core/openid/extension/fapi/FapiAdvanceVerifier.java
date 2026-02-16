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
import org.idp.server.core.openid.oauth.AuthorizationProfile;
import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.verifier.AuthorizationRequestVerifier;
import org.idp.server.core.openid.oauth.verifier.base.OAuthRequestBaseVerifier;
import org.idp.server.core.openid.oauth.verifier.base.OidcRequestBaseVerifier;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.jose.JsonWebTokenClaims;

public class FapiAdvanceVerifier implements AuthorizationRequestVerifier {

  OAuthRequestBaseVerifier oAuthRequestBaseVerifier = new OAuthRequestBaseVerifier();
  OidcRequestBaseVerifier oidcRequestBaseVerifier = new OidcRequestBaseVerifier();

  public AuthorizationProfile profile() {
    return AuthorizationProfile.FAPI_ADVANCE;
  }

  @Override
  public void verify(OAuthRequestContext context) {
    throwIfExceptionInvalidConfig(context);
    if (context.isOidcRequest()) {
      oidcRequestBaseVerifier.verify(context);
    } else {
      oAuthRequestBaseVerifier.verify(context);
    }
    throwExceptionIfNotRequestParameterPattern(context);
    throwExceptionIfInvalidResponseTypeAndResponseMode(context);
    throwIfNotSenderConstrainedAccessToken(context);
    throwExceptionIfNotContainExpAndNbfAndExp60minutesLongerThanNbf(context);
    throwExceptionIfNotContainsAud(context);
    throwExceptionIfClientSecretPostOrClientSecretBasicOrClientSecretJwt(context);
    throwExceptionIfPublicClient(context);
    throwExceptionIfNotContainNbfAnd60minutesLongerThan(context);
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
   * shall require a JWS signed JWT request object passed by value with the request parameter or by
   * reference with the request_uri parameter;
   *
   * <p>FAPI 1.0 Advanced Final, Section 5.2.2 (clause 1): shall require a JWS signed JWT request
   * object. Request objects with alg:none (unsigned) are not acceptable.
   *
   * @see <a
   *     href="https://openid.net/specs/openid-financial-api-part-2-1_0.html#authorization-server">FAPI
   *     1.0 Advanced Final Section 5.2.2</a>
   */
  void throwExceptionIfNotRequestParameterPattern(OAuthRequestContext context) {
    if (!context.isRequestParameterPattern()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Advance profile, shall require a JWS signed JWT request object passed by value with the request parameter or by reference with the request_uri parameter",
          context);
    }
    if (context.isUnsignedRequestObject()) {
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
    if (exp.getTime() - nbf.getTime() > 3600001) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object",
          "When FAPI Advance profile, shall require the request object to contain an exp claim that has a lifetime of no longer than 60 minutes after the nbf claim",
          context);
    }
  }

  /**
   * shall require the aud claim in the request object to be, or to be an array containing, the OP's
   * Issuer Identifier URL;
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
    if (!aud.contains(context.tokenIssuer().value())) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object",
          String.format(
              "When FAPI Advance profile, shall require the aud claim in the request object to be, or to be an array containing, the OP's Issuer Identifier URL (%s)",
              String.join(" ", aud)),
          context);
    }
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

  /** shall not support public clients; */
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
    if (now.getTime() - nbf.getTime() > 3600001) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object",
          "When FAPI Advance profile, shall require the request object to contain an nbf claim that is no longer than 60 minutes in the past",
          context);
    }
  }
}

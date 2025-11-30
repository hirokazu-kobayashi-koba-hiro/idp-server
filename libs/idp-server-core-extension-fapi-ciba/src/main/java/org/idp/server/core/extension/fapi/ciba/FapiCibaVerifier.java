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

package org.idp.server.core.extension.fapi.ciba;

import java.util.Date;
import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationUnauthorizedException;
import org.idp.server.core.openid.oauth.type.ciba.BackchannelTokenDeliveryMode;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.jose.JsonWebTokenClaims;

/**
 * FAPI CIBA Profile Verifier
 *
 * <p>Implements the FAPI CIBA Profile security requirements as specified in FAPI CIBA Security
 * Profile Section 5.2.2.
 *
 * <p>This verifier enforces the following requirements:
 *
 * <ol>
 *   <li>Signed Request Object is mandatory
 *   <li>Request Object lifetime verification (nbf/exp, max 60 minutes)
 *   <li>Signing algorithm restrictions (PS256 or ES256 only)
 *   <li>Confidential Client requirement
 *   <li>binding_message requirement when no unique authorization context exists
 *   <li>Push mode prohibition
 *   <li>Client authentication method restrictions (private_key_jwt, tls_client_auth,
 *       self_signed_tls_client_auth only)
 *   <li>TLS requirements (enforced at transport layer)
 * </ol>
 *
 * @see <a href="https://openid.net/specs/openid-financial-api-ciba-ID1.html#section-5.2.2">FAPI
 *     CIBA Security Profile Section 5.2.2</a>
 */
public class FapiCibaVerifier {

  private static final long MAX_REQUEST_OBJECT_LIFETIME_MS = 60 * 60 * 1000; // 60 minutes

  public void verify(CibaRequestContext context) {
    throwExceptionIfNotSignedRequestObject(context);
    throwExceptionIfMissingIat(context);
    throwExceptionIfInvalidRequestObjectLifetime(context);
    throwExceptionIfInvalidSigningAlgorithm(context);
    throwExceptionIfNotConfidentialClient(context);
    throwExceptionIfMissingBindingMessage(context);
    throwExceptionIfPushMode(context);
    throwExceptionIfInvalidClientAuthenticationMethod(context);
    throwExceptionIfNotContainsAud(context);
    throwIfNotSenderConstrainedAccessToken(context);
  }

  /**
   * 5.2.2.1 shall require signed request objects
   *
   * <p>FAPI CIBA requires all authentication requests to be made using signed request objects.
   */
  void throwExceptionIfNotSignedRequestObject(CibaRequestContext context) {
    if (!context.isRequestObjectPattern()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          "FAPI CIBA Profile requires signed request object. Request must include 'request' parameter with a signed JWT.");
    }
  }

  /**
   * Validates 'iat' claim presence in request object.
   *
   * <p><strong>Note:</strong> This requirement is NOT explicitly mandated in FAPI Part 2 Section
   * 5.2.2. The specification only requires 'nbf' (5.2.2-18) and 'exp' (5.2.2-14) claims.
   *
   * <p>However, the OpenID Foundation FAPI CIBA Conformance Test Suite (test:
   * fapi-ciba-id1-ensure-request-object-missing-iat-fails) validates that the 'iat' claim is
   * present. This validation is implemented to pass the conformance test suite.
   *
   * @see <a
   *     href="https://openid.net/specs/openid-financial-api-part-2-1_0.html#authorization-server">FAPI
   *     Part 2 Section 5.2.2</a>
   */
  void throwExceptionIfMissingIat(CibaRequestContext context) {
    JoseContext joseContext = context.joseContext();
    if (!joseContext.exists()) {
      return; // Will be caught by other validators
    }

    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasIat()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request", "FAPI CIBA Profile requires 'iat' claim in request object.");
    }
  }

  /**
   * 5.2.2.2 Request Object Lifetime Verification (FAPI Part 2 5.2.2-13)
   *
   * <p>shall require the request object to contain an exp claim that has a lifetime of no longer
   * than 60 minutes after the nbf claim
   *
   * <p>5.2.2.3 Request Object nbf Validation (FAPI Part 2 5.2.2-17)
   *
   * <p>shall require the request object to contain an nbf claim that is no longer than 60 minutes
   * in the past
   */
  void throwExceptionIfInvalidRequestObjectLifetime(CibaRequestContext context) {
    JoseContext joseContext = context.joseContext();
    if (!joseContext.exists()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request", "FAPI CIBA Profile requires valid request object with claims.");
    }

    JsonWebTokenClaims claims = joseContext.claims();

    if (!claims.hasExp()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request", "FAPI CIBA Profile requires 'exp' claim in request object.");
    }

    if (!claims.hasNbf()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request", "FAPI CIBA Profile requires 'nbf' claim in request object.");
    }

    Date nbf = claims.getNbf();
    Date exp = claims.getExp();
    Date now = new Date();

    // 5.2.2-13: exp - nbf <= 60 minutes
    long lifetimeMs = exp.getTime() - nbf.getTime();

    if (lifetimeMs > MAX_REQUEST_OBJECT_LIFETIME_MS) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          String.format(
              "FAPI CIBA Profile requires request object lifetime to be no longer than 60 minutes. Current lifetime: %d minutes",
              lifetimeMs / 60000));
    }

    if (lifetimeMs <= 0) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          "FAPI CIBA Profile requires 'exp' to be after 'nbf' in request object.");
    }

    // 5.2.2-17: nbf must not be more than 60 minutes in the past
    long nbfAgeMs = now.getTime() - nbf.getTime();

    if (nbfAgeMs > MAX_REQUEST_OBJECT_LIFETIME_MS) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          String.format(
              "FAPI CIBA Profile requires 'nbf' claim to be no longer than 60 minutes in the past. Current age: %d minutes",
              nbfAgeMs / 60000));
    }
  }

  /**
   * 5.2.2.3 Signing Algorithm Restrictions (FAPI CIBA 7.10)
   *
   * <p>shall use PS256 or ES256 algorithms
   *
   * <p>should not use algorithms that use RSASSA-PKCS1-v1_5 (e.g. RS256)
   *
   * <p>shall not use none
   *
   * <p>5.2.2.4 Key Size Requirements (FAPI Part 1 5.2.2-5, 5.2.2-6)
   *
   * <p>shall require and use a key of size 2048 bits or larger for RSA algorithms (PS256)
   *
   * <p>shall require and use a key of size 160 bits or larger for elliptic curve algorithms (ES256)
   */
  void throwExceptionIfInvalidSigningAlgorithm(CibaRequestContext context) {
    JoseContext joseContext = context.joseContext();
    if (!joseContext.hasJsonWebSignature()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request", "FAPI CIBA Profile requires signed request object.");
    }

    String algorithm = joseContext.jsonWebSignature().algorithm();

    // FAPI CIBA 7.10: shall use PS256 or ES256 algorithms
    if (!"PS256".equals(algorithm) && !"ES256".equals(algorithm)) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          String.format(
              "FAPI CIBA Profile requires signing algorithm to be PS256 or ES256. Current algorithm: %s",
              algorithm));
    }

    // FAPI Part 1 5.2.2-5, 5.2.2-6: Key size requirements
    int keySize = joseContext.jsonWebKey().keySize();

    if ("PS256".equals(algorithm) && keySize < 2048) {
      // RSA algorithms require 2048 bits or larger
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          String.format(
              "FAPI CIBA Profile requires RSA key size to be 2048 bits or larger. Current key size: %d bits",
              keySize));
    }

    if ("ES256".equals(algorithm) && keySize < 160) {
      // Elliptic curve algorithms require 160 bits or larger (ES256 uses 256 bits)
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          String.format(
              "FAPI CIBA Profile requires elliptic curve key size to be 160 bits or larger. Current key size: %d bits",
              keySize));
    }
  }

  /**
   * 5.2.2.4 Confidential Client Requirement
   *
   * <p>shall only support confidential clients
   */
  void throwExceptionIfNotConfidentialClient(CibaRequestContext context) {
    ClientAuthenticationType authenticationType = context.clientAuthenticationType();

    // Public clients use 'none' authentication type
    if (authenticationType.isNone()) {
      throw new BackchannelAuthenticationUnauthorizedException(
          "invalid_client",
          "FAPI CIBA Profile requires confidential clients. Public clients are not allowed.");
    }
  }

  /**
   * 5.2.2.5 Binding Message Requirement
   *
   * <p>shall require a binding_message parameter when there is no unique authorization context (as
   * per RFC 9396, authorization_details provides unique context)
   */
  void throwExceptionIfMissingBindingMessage(CibaRequestContext context) {
    // If authorization_details exists, it provides unique authorization context
    if (context.hasAuthorizationDetails()) {
      return;
    }

    // Otherwise, binding_message is required
    if (!context.backchannelAuthenticationRequest().hasBindingMessage()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          "FAPI CIBA Profile requires 'binding_message' parameter when authorization_details is not present.");
    }
  }

  /**
   * 5.2.2.6 Push Mode Prohibition
   *
   * <p>shall not support push mode for token delivery
   */
  void throwExceptionIfPushMode(CibaRequestContext context) {
    BackchannelTokenDeliveryMode deliveryMode =
        context.clientConfiguration().backchannelTokenDeliveryMode();

    if (deliveryMode.isPushMode()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          "FAPI CIBA Profile does not support push mode for token delivery. Use poll or ping mode.");
    }
  }

  /**
   * 5.2.2.7 Client Authentication Method Restrictions
   *
   * <p>shall require client authentication using one of the following methods: - private_key_jwt -
   * tls_client_auth - self_signed_tls_client_auth
   *
   * <p>client_secret_jwt, client_secret_basic, and client_secret_post are not allowed
   *
   * <p>According to CIBA Core Section 13, unsupported authentication method errors should return
   * HTTP 401 with error code "invalid_client".
   *
   * @see <a
   *     href="https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html#rfc.section.13">CIBA
   *     Core Section 13</a>
   */
  void throwExceptionIfInvalidClientAuthenticationMethod(CibaRequestContext context) {
    ClientAuthenticationType authenticationType = context.clientAuthenticationType();

    boolean isValid =
        authenticationType.isPrivateKeyJwt()
            || authenticationType.isTlsClientAuth()
            || authenticationType.isSelfSignedTlsClientAuth();

    if (!isValid) {
      throw new BackchannelAuthenticationUnauthorizedException(
          "invalid_client",
          String.format(
              "FAPI CIBA Profile requires client authentication method to be one of: private_key_jwt, tls_client_auth, self_signed_tls_client_auth. Current method: %s",
              authenticationType.name()));
    }
  }

  /**
   * FAPI Advance 5.2.2-11: aud claim requirement
   *
   * <p>shall require the aud claim in the request object to be, or to be an array containing, the
   * OP's Issuer Identifier URL
   */
  void throwExceptionIfNotContainsAud(CibaRequestContext context) {
    JoseContext joseContext = context.joseContext();
    if (!joseContext.exists()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request", "FAPI CIBA Profile requires valid request object with claims.");
    }

    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasAud()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          "FAPI CIBA Profile requires the request object to contain an aud claim.");
    }

    java.util.List<String> aud = claims.getAud();
    String issuer = context.tokenIssuer().value();
    if (!aud.contains(issuer)) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          String.format(
              "FAPI CIBA Profile requires the aud claim in the request object to contain the OP's Issuer Identifier URL. Expected: %s, Actual: %s",
              issuer, String.join(", ", aud)));
    }
  }

  /**
   * FAPI Advance 5.2.2-5: Sender-constrained access tokens
   *
   * <p>shall only issue sender-constrained access tokens (MTLS binding required)
   */
  void throwIfNotSenderConstrainedAccessToken(CibaRequestContext context) {
    if (!context.serverConfiguration().isTlsClientCertificateBoundAccessTokens()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          "FAPI CIBA Profile requires sender-constrained access tokens, but server tls_client_certificate_bound_access_tokens is false.");
    }
    if (!context.clientConfiguration().isTlsClientCertificateBoundAccessTokens()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          "FAPI CIBA Profile requires sender-constrained access tokens, but client tls_client_certificate_bound_access_tokens is false.");
    }
  }
}

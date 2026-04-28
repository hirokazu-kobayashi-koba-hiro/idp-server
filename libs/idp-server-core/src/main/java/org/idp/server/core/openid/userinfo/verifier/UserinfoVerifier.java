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

package org.idp.server.core.openid.userinfo.verifier;

import java.time.LocalDateTime;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertification;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertificationThumbprint;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertificationThumbprintCalculator;
import org.idp.server.core.openid.oauth.dpop.AccessTokenHashCalculator;
import org.idp.server.core.openid.oauth.dpop.DPoPProof;
import org.idp.server.core.openid.oauth.dpop.DPoPProofInvalidException;
import org.idp.server.core.openid.oauth.dpop.DPoPProofVerifiedResult;
import org.idp.server.core.openid.oauth.dpop.DPoPProofVerifier;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.Subject;
import org.idp.server.core.openid.token.AccessToken;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenInvalidException;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.x509.X509CertInvalidException;

/**
 * Verifier for UserInfo endpoint requests.
 *
 * <p>OIDC Core 1.0 Section 5.3.1: The Client sends the UserInfo Request using either HTTP GET or
 * HTTP POST. The Access Token obtained from an OpenID Connect Authentication Request MUST be sent
 * as a Bearer Token, per Section 2 of OAuth 2.0 Bearer Token Usage [RFC6750].
 *
 * <p>OIDC Core 1.0 Section 5.3.3: When an error condition occurs, the UserInfo Endpoint returns an
 * Error Response as defined in Section 3 of OAuth 2.0 Bearer Token Usage [RFC6750].
 *
 * <p>RFC 6749 Section 4.4: The client credentials grant type MUST only be used by confidential
 * clients. Since client_credentials tokens have no end-user subject, the UserInfo Endpoint MUST
 * reject them with invalid_token.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#UserInfo">OIDC Core 1.0
 *     Section 5.3</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6750#section-3">RFC 6750 Section 3</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.4">RFC 6749 Section 4.4</a>
 */
public class UserinfoVerifier {

  OAuthToken oAuthToken;
  ClientCert clientCert;
  DPoPProof dpopProof;
  String httpMethod;
  String httpUri;

  public UserinfoVerifier(OAuthToken oAuthToken, ClientCert clientCert) {
    this.oAuthToken = oAuthToken;
    this.clientCert = clientCert;
  }

  public UserinfoVerifier(
      OAuthToken oAuthToken,
      ClientCert clientCert,
      DPoPProof dpopProof,
      String httpMethod,
      String httpUri) {
    this.oAuthToken = oAuthToken;
    this.clientCert = clientCert;
    this.dpopProof = dpopProof;
    this.httpMethod = httpMethod;
    this.httpUri = httpUri;
  }

  /** Verify token validity: existence, expiration, subject presence, and client certificate. */
  public void verifyToken() {
    throwExceptionIfNotFoundToken();
    throwExceptionIfNoSubject();
    throwExceptionIfUnMatchClientCert();
    throwExceptionIfUnMatchDPoPProof();
  }

  /** Verify user state: existence and active status. */
  public void verifyUser(User user) {
    throwExceptionIfNotFoundUser(user);
    throwExceptionIfInactiveUser(user);
  }

  /**
   * Verify access token existence and expiration.
   *
   * <p>RFC 6750 Section 3.1: If the request lacks any authentication information, the resource
   * server SHOULD NOT include an error code. If the access token is expired, the resource server
   * returns invalid_token.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6750#section-3.1">RFC 6750 Section 3.1</a>
   */
  void throwExceptionIfNotFoundToken() {
    if (!oAuthToken.exists()) {
      throw new TokenInvalidException("not found token");
    }
    LocalDateTime now = SystemDateTime.now();
    if (oAuthToken.isExpiredAccessToken(now)) {
      throw new TokenInvalidException("token is expired");
    }
  }

  void throwExceptionIfUnMatchDPoPProof() {
    AccessToken accessToken = oAuthToken.accessToken();
    if (!accessToken.hasDPoPBinding()) {
      return;
    }
    if (dpopProof == null || !dpopProof.exists()) {
      throw new TokenInvalidException(
          "access token is DPoP-bound, but DPoP proof header is missing");
    }
    try {
      String accessTokenValue = oAuthToken.accessTokenEntity().value();
      String ath = new AccessTokenHashCalculator(accessTokenValue).calculate();
      DPoPProofVerifier verifier = new DPoPProofVerifier();
      DPoPProofVerifiedResult result = verifier.verify(dpopProof, httpMethod, httpUri, ath);
      if (!accessToken.matchJwkThumbprint(result.jwkThumbprint())) {
        throw new TokenInvalidException(
            "DPoP proof JWK thumbprint does not match the access token binding");
      }
    } catch (DPoPProofInvalidException e) {
      throw new TokenInvalidException("DPoP proof validation failed: " + e.getMessage());
    }
  }

  /**
   * Reject tokens without a subject claim.
   *
   * <p>OIDC Core 1.0 Section 5.3: The UserInfo Endpoint returns Claims about the authenticated
   * End-User. Tokens issued via client_credentials grant (RFC 6749 Section 4.4) have no end-user
   * and therefore no subject.
   */
  void throwExceptionIfNoSubject() {
    Subject subject = oAuthToken.subject();
    if (subject == null || !subject.exists()) {
      throw new TokenInvalidException(
          "token has no subject; UserInfo endpoint requires a token issued for an end-user");
    }
  }

  /**
   * Verify sender-constrained token binding.
   *
   * <p>RFC 8705 Section 3: When a mutual TLS client certificate-bound access token is used, the
   * resource server MUST verify that the client certificate hash matches the certificate thumbprint
   * in the token's cnf claim.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc8705#section-3">RFC 8705 Section 3</a>
   */
  void throwExceptionIfUnMatchClientCert() {
    if (!oAuthToken.hasClientCertification()) {
      return;
    }
    if (!clientCert.exists()) {
      throw new TokenInvalidException(
          "access token is sender constrained, but mtls client cert does not exists");
    }
    try {
      ClientCertification clientCertification = ClientCertification.parse(clientCert.plainValue());
      ClientCertificationThumbprintCalculator calculator =
          new ClientCertificationThumbprintCalculator(clientCertification);
      ClientCertificationThumbprint thumbprint = calculator.calculate();
      AccessToken accessToken = oAuthToken.accessToken();
      if (!accessToken.matchThumbprint(thumbprint)) {
        throw new TokenInvalidException("access token and mtls client cert is unmatch");
      }
    } catch (X509CertInvalidException e) {
      throw new TokenInvalidException(
          "access token is sender constrained, but mtls client cert is invalid format", e);
    }
  }

  /**
   * Verify that the user identified by the token's subject exists.
   *
   * <p>OIDC Core 1.0 Section 5.3.3: If the sub (subject) Claim in the UserInfo Response is not
   * equivalent to the one in the ID Token, the Client MUST treat the response as an error. The user
   * may have been deleted after the token was issued.
   *
   * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#UserInfoError">OIDC Core
   *     1.0 Section 5.3.3</a>
   */
  void throwExceptionIfNotFoundUser(User user) {
    if (!user.exists()) {
      throw new TokenInvalidException("not found user");
    }
  }

  /**
   * Verify that the user is in an active state.
   *
   * <p>A user may be suspended or deactivated by an administrator after the token was issued. The
   * UserInfo endpoint should not return claims for inactive users.
   */
  void throwExceptionIfInactiveUser(User user) {
    if (!user.isActive()) {
      throw new TokenInvalidException(
          String.format(
              "user is not active (id: %s, status: %s)", user.sub(), user.status().name()));
    }
  }
}

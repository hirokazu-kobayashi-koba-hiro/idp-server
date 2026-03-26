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

package org.idp.server.core.openid.token.verifier;

import java.util.Optional;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.AvailableFederation;
import org.idp.server.core.openid.oauth.type.oauth.SubjectToken;
import org.idp.server.core.openid.oauth.type.oauth.SubjectTokenType;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.core.openid.token.service.FederationJwtVerifier;
import org.idp.server.core.openid.token.service.SubjectTokenVerificationResult;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebTokenClaims;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * JwtSubjectTokenVerificationStrategy
 *
 * <p>Verifies subject_token as a JWT: parses the JWS, resolves the federation by issuer, verifies
 * the signature against the federation's JWKS, and validates standard JWT claims (iss, sub, aud,
 * exp) per RFC 7523.
 *
 * <p>Handles both {@code urn:ietf:params:oauth:token-type:jwt} and {@code
 * urn:ietf:params:oauth:token-type:id_token} token types.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-3">RFC 8693 Section 3</a>
 */
public class JwtSubjectTokenVerificationStrategy implements SubjectTokenVerificationStrategy {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(JwtSubjectTokenVerificationStrategy.class);

  FederationJwtVerifier federationJwtVerifier;

  public JwtSubjectTokenVerificationStrategy(FederationJwtVerifier federationJwtVerifier) {
    this.federationJwtVerifier = federationJwtVerifier;
  }

  public static final SubjectTokenType TYPE =
      new SubjectTokenType("urn:ietf:params:oauth:token-type:jwt");

  @Override
  public SubjectTokenType type() {
    return TYPE;
  }

  @Override
  public SubjectTokenVerificationResult verify(
      TokenRequestContext context, SubjectToken subjectToken) {
    try {
      AuthorizationServerConfiguration serverConfiguration = context.serverConfiguration();

      JsonWebSignature jws = subjectToken.parse();
      JsonWebTokenClaims claims = jws.claims();
      String issuer = claims.getIss();

      AvailableFederation federation = findFederationByIssuer(context, issuer);

      federationJwtVerifier.verifyExternalIdpSignature(jws, federation);

      String expectedAudience = serverConfiguration.tokenIssuer().value();
      TokenExchangeGrantVerifier verifier =
          new TokenExchangeGrantVerifier(claims, expectedAudience);
      verifier.verify();

      return new SubjectTokenVerificationResult(
          federation, claims.getSub(), federation.resolveSubjectClaimMapping(), claims.toMap());
    } catch (TokenBadRequestException e) {
      throw e;
    } catch (JoseInvalidException e) {
      log.warn("subject_token JWT verification failed: {}", e.getMessage());
      throw new TokenBadRequestException(
          "invalid_grant", "Invalid subject_token JWT: " + e.getMessage());
    } catch (RuntimeException e) {
      log.error("Unexpected error verifying subject_token JWT", e);
      throw new TokenBadRequestException(
          "server_error", "Unexpected error verifying subject_token JWT");
    }
  }

  private AvailableFederation findFederationByIssuer(TokenRequestContext context, String issuer) {
    Optional<AvailableFederation> federation = context.findTokenExchangeFederationByIssuer(issuer);
    if (federation.isEmpty()) {
      throw new TokenBadRequestException(
          "invalid_grant", String.format("Issuer '%s' is not trusted for this client", issuer));
    }
    return federation.get();
  }
}

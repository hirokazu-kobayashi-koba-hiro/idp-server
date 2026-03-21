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
import org.idp.server.core.openid.token.service.ExternalTokenIntrospector;
import org.idp.server.core.openid.token.service.FederationJwtVerifier;
import org.idp.server.core.openid.token.service.SubjectTokenVerificationResult;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebTokenClaims;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * SubjectTokenVerifier
 *
 * <p>Verifies a subject_token in Token Exchange (RFC 8693) and produces a {@link
 * SubjectTokenVerificationResult}. Supports JWT signature verification and external IdP
 * introspection.
 *
 * <p>All verification errors are converted to {@link TokenBadRequestException} so callers do not
 * need to handle low-level exceptions.
 */
public class SubjectTokenVerifier {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(SubjectTokenVerifier.class);

  FederationJwtVerifier federationJwtVerifier;
  ExternalTokenIntrospector externalTokenIntrospector;

  public SubjectTokenVerifier(
      FederationJwtVerifier federationJwtVerifier,
      ExternalTokenIntrospector externalTokenIntrospector) {
    this.federationJwtVerifier = federationJwtVerifier;
    this.externalTokenIntrospector = externalTokenIntrospector;
  }

  public SubjectTokenVerificationResult verify(TokenRequestContext context) {
    SubjectTokenType subjectTokenType = context.subjectTokenType();
    SubjectToken subjectToken = context.subjectToken();

    if (subjectTokenType.isAlwaysJwt()) {
      return verifyAsJwt(context, subjectToken);
    }

    if (subjectTokenType.isAccessToken()) {
      return verifyAccessToken(context, subjectToken);
    }

    throw new TokenBadRequestException(
        "invalid_request", "Unsupported subject_token_type: " + subjectTokenType.value());
  }

  private SubjectTokenVerificationResult verifyAccessToken(
      TokenRequestContext context, SubjectToken subjectToken) {
    Optional<AvailableFederation> introspectionFederation =
        context.findTokenExchangeIntrospectionFederation();
    if (introspectionFederation.isPresent()
        && introspectionFederation.get().isIntrospectionVerification()) {
      return verifyByIntrospection(subjectToken, introspectionFederation.get());
    }

    return verifyAsJwt(context, subjectToken);
  }

  private SubjectTokenVerificationResult verifyAsJwt(
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
    }
  }

  private SubjectTokenVerificationResult verifyByIntrospection(
      SubjectToken subjectToken, AvailableFederation federation) {
    ExternalTokenIntrospector.ExternalIntrospectionResult result =
        externalTokenIntrospector.introspect(subjectToken.value(), federation);

    if (!result.isActive()) {
      log.warn(
          "External introspection returned active=false. issuer={}, endpoint={}",
          federation.issuer(),
          federation.introspectionEndpoint());
      throw new TokenBadRequestException(
          "invalid_grant", "subject_token is not active according to external IdP introspection");
    }

    if (!result.hasSubject()) {
      log.warn(
          "External introspection response missing 'sub' claim. issuer={}, claims={}",
          federation.issuer(),
          result.claims());
      throw new TokenBadRequestException(
          "invalid_grant", "External IdP introspection response does not contain a 'sub' claim");
    }

    return new SubjectTokenVerificationResult(
        federation, result.subject(), federation.resolveSubjectClaimMapping(), result.claims());
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

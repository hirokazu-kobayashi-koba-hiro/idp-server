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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.idp.server.core.openid.oauth.configuration.client.AvailableFederation;
import org.idp.server.core.openid.oauth.type.oauth.SecurityToken;
import org.idp.server.core.openid.oauth.type.oauth.SubjectTokenType;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.core.openid.token.service.ExternalIntrospectionResult;
import org.idp.server.core.openid.token.service.ExternalTokenIntrospector;
import org.idp.server.core.openid.token.service.SubjectTokenVerificationResult;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * AccessTokenSubjectTokenVerificationStrategy
 *
 * <p>Verifies subject_token of type {@code urn:ietf:params:oauth:token-type:access_token}. If the
 * federation is configured with introspection, verifies via external IdP token introspection (RFC
 * 7662). Otherwise, falls back to JWT verification via {@link JwtSubjectTokenVerificationStrategy}.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-3">RFC 8693 Section 3</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7662">RFC 7662 - Token Introspection</a>
 */
public class AccessTokenSubjectTokenVerificationStrategy
    implements SubjectTokenVerificationStrategy {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AccessTokenSubjectTokenVerificationStrategy.class);

  JwtSubjectTokenVerificationStrategy jwtStrategy;
  ExternalTokenIntrospector externalTokenIntrospector;

  public AccessTokenSubjectTokenVerificationStrategy(
      JwtSubjectTokenVerificationStrategy jwtStrategy,
      ExternalTokenIntrospector externalTokenIntrospector) {
    this.jwtStrategy = jwtStrategy;
    this.externalTokenIntrospector = externalTokenIntrospector;
  }

  public static final SubjectTokenType TYPE =
      new SubjectTokenType("urn:ietf:params:oauth:token-type:access_token");

  @Override
  public SubjectTokenType type() {
    return TYPE;
  }

  @Override
  public SubjectTokenVerificationResult verify(
      TokenRequestContext context, SecurityToken securityToken) {
    Optional<AvailableFederation> introspectionFederation =
        context.findTokenExchangeIntrospectionFederation();
    if (introspectionFederation.isPresent()) {
      return verifyByIntrospection(securityToken, introspectionFederation.get());
    }

    return jwtStrategy.verify(context, securityToken);
  }

  /**
   * Verifies the subject_token via external IdP token introspection (RFC 7662). Validates that the
   * introspection response contains {@code active: true} and a {@code sub} claim. Optionally
   * fetches additional user claims via configured {@code userinfo_http_requests}.
   */
  private SubjectTokenVerificationResult verifyByIntrospection(
      SecurityToken securityToken, AvailableFederation federation) {
    ExternalIntrospectionResult result =
        externalTokenIntrospector.introspect(securityToken.value(), federation);

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
          "External introspection response missing 'sub' claim. issuer={}, endpoint={}, claimKeys={}",
          federation.issuer(),
          federation.introspectionEndpoint(),
          result.claims().keySet());
      throw new TokenBadRequestException(
          "invalid_grant", "External IdP introspection response does not contain a 'sub' claim");
    }

    Map<String, Object> claims = new HashMap<>(result.claims());

    if (federation.hasUserinfoHttpRequests()) {
      Map<String, Object> userinfoResult =
          externalTokenIntrospector.fetchUserinfo(securityToken.value(), federation);
      if (!userinfoResult.isEmpty()) {
        claims.putAll(userinfoResult);
      }
    }

    return new SubjectTokenVerificationResult(
        federation, result.subject(), federation.resolveSubjectClaimMapping(), claims);
  }
}

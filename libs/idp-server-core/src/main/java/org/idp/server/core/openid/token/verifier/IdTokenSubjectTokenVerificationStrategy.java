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

import org.idp.server.core.openid.oauth.type.oauth.SubjectToken;
import org.idp.server.core.openid.oauth.type.oauth.SubjectTokenType;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.core.openid.token.service.SubjectTokenVerificationResult;

/**
 * IdTokenSubjectTokenVerificationStrategy
 *
 * <p>Verifies subject_token of type {@code urn:ietf:params:oauth:token-type:id_token}. Extends JWT
 * verification with additional OIDC ID Token validation per OpenID Connect Core Section 3.1.3.7.
 *
 * <p>OIDC Core Section 3.1.3.7 - ID Token Validation (applicable to Token Exchange context):
 *
 * <ul>
 *   <li>2. The Issuer Identifier MUST exactly match the value of the iss Claim. (covered by JWT
 *       strategy via federation lookup)
 *   <li>3. The Client MUST validate that the aud Claim contains its client_id value. (covered by
 *       JWT strategy via TokenExchangeGrantVerifier)
 *   <li>6. The Client MUST validate the signature. (covered by JWT strategy via
 *       FederationJwtVerifier)
 *   <li>9. The current time MUST be before the time represented by the exp Claim. (covered by JWT
 *       strategy via TokenExchangeGrantVerifier)
 *   <li>sub MUST be present in an ID Token. (this strategy)
 * </ul>
 *
 * <p>Note: nonce validation (Section 3.1.3.7 step 11) is not applicable in Token Exchange context
 * because the authorization server did not issue the original authentication request containing the
 * nonce.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation">OIDC Core
 *     Section 3.1.3.7</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-3">RFC 8693 Section 3</a>
 */
public class IdTokenSubjectTokenVerificationStrategy implements SubjectTokenVerificationStrategy {

  JwtSubjectTokenVerificationStrategy jwtStrategy;

  public IdTokenSubjectTokenVerificationStrategy(JwtSubjectTokenVerificationStrategy jwtStrategy) {
    this.jwtStrategy = jwtStrategy;
  }

  public static final SubjectTokenType TYPE =
      new SubjectTokenType("urn:ietf:params:oauth:token-type:id_token");

  @Override
  public SubjectTokenType type() {
    return TYPE;
  }

  @Override
  public SubjectTokenVerificationResult verify(
      TokenRequestContext context, SubjectToken subjectToken) {
    SubjectTokenVerificationResult result = jwtStrategy.verify(context, subjectToken);

    // OIDC Core Section 2 - ID Token:
    // "sub REQUIRED. Subject Identifier."
    if (result.subject() == null || result.subject().isEmpty()) {
      throw new TokenBadRequestException(
          "invalid_grant",
          "ID Token subject_token does not contain a 'sub' claim (OIDC Core Section 2)");
    }

    return result;
  }
}

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
import org.idp.server.core.openid.oauth.type.oauth.SubjectTokenType;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.core.openid.token.service.ExternalTokenIntrospector;
import org.idp.server.core.openid.token.service.FederationJwtVerifier;

/**
 * SubjectTokenVerificationStrategies
 *
 * <p>Registry of {@link SubjectTokenVerificationStrategy} instances, keyed by token type identifier
 * (RFC 8693 Section 3). Core token types (jwt, id_token, access_token) are registered in the
 * constructor. Extension token types (e.g., SAML) are added via {@code extensionStrategies}.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-3">RFC 8693 Section 3</a>
 */
public class SubjectTokenVerificationStrategies {

  Map<SubjectTokenType, SubjectTokenVerificationStrategy> values = new HashMap<>();

  public SubjectTokenVerificationStrategies(
      FederationJwtVerifier federationJwtVerifier,
      ExternalTokenIntrospector externalTokenIntrospector,
      Map<SubjectTokenType, SubjectTokenVerificationStrategy> extensionStrategies) {
    JwtSubjectTokenVerificationStrategy jwtStrategy =
        new JwtSubjectTokenVerificationStrategy(federationJwtVerifier);
    IdTokenSubjectTokenVerificationStrategy idTokenStrategy =
        new IdTokenSubjectTokenVerificationStrategy(jwtStrategy);
    AccessTokenSubjectTokenVerificationStrategy accessTokenStrategy =
        new AccessTokenSubjectTokenVerificationStrategy(jwtStrategy, externalTokenIntrospector);

    values.put(jwtStrategy.type(), jwtStrategy);
    values.put(idTokenStrategy.type(), idTokenStrategy);
    values.put(accessTokenStrategy.type(), accessTokenStrategy);
    values.putAll(extensionStrategies);
  }

  public SubjectTokenVerificationStrategy get(SubjectTokenType subjectTokenType) {
    SubjectTokenVerificationStrategy strategy = values.get(subjectTokenType);
    if (strategy == null) {
      throw new TokenBadRequestException(
          "invalid_request", "Unsupported subject_token_type: " + subjectTokenType.value());
    }
    return strategy;
  }
}

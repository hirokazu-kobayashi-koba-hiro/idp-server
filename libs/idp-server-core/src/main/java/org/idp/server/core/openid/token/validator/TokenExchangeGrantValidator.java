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

package org.idp.server.core.openid.token.validator;

import org.idp.server.core.openid.oauth.type.oauth.GrantType;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;

/**
 * TokenExchangeGrantValidator
 *
 * <p>Validates token exchange request parameters per RFC 8693 Section 2.1.
 *
 * <p>RFC 8693 Section 2.1:
 *
 * <blockquote>
 *
 * The value "urn:ietf:params:oauth:grant-type:token-exchange" indicates that a token exchange is
 * being performed.
 *
 * </blockquote>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-2.1">RFC 8693 Section 2.1</a>
 */
public class TokenExchangeGrantValidator {

  TokenRequestContext tokenRequestContext;

  public TokenExchangeGrantValidator(TokenRequestContext tokenRequestContext) {
    this.tokenRequestContext = tokenRequestContext;
  }

  public void validate() {
    throwExceptionIfUnSupportedGrantTypeWithServer();
    throwExceptionIfUnSupportedGrantTypeWithClient();
    throwExceptionIfSubjectTokenNotProvided();
    throwExceptionIfSubjectTokenTypeNotProvided();
    throwExceptionIfActorTokenTypeNotProvidedWhenActorTokenPresent();
  }

  /**
   * RFC 8693 Section 2.1:
   *
   * <blockquote>
   *
   * actor_token_type REQUIRED when the actor_token parameter is present in the request but MUST NOT
   * be included otherwise.
   *
   * </blockquote>
   */
  void throwExceptionIfActorTokenTypeNotProvidedWhenActorTokenPresent() {
    if (tokenRequestContext.hasActorToken() && !tokenRequestContext.hasActorTokenType()) {
      throw new TokenBadRequestException(
          "invalid_request", "actor_token_type parameter is required when actor_token is present");
    }
  }

  /**
   * RFC 8693 Section 2.1:
   *
   * <blockquote>
   *
   * subject_token REQUIRED. A security token that represents the identity of the party on behalf of
   * whom the request is being made.
   *
   * </blockquote>
   */
  void throwExceptionIfSubjectTokenNotProvided() {
    if (!tokenRequestContext.hasSubjectToken()) {
      throw new TokenBadRequestException(
          "invalid_request", "subject_token parameter is required for Token Exchange Grant");
    }
  }

  /**
   * RFC 8693 Section 2.1:
   *
   * <blockquote>
   *
   * subject_token_type REQUIRED. An identifier, as described in Section 3, that indicates the type
   * of the security token in the subject_token parameter.
   *
   * </blockquote>
   *
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-3">RFC 8693 Section 3 -
   *     Token Type Identifiers</a>
   */
  void throwExceptionIfSubjectTokenTypeNotProvided() {
    if (!tokenRequestContext.hasSubjectTokenType()) {
      throw new TokenBadRequestException(
          "invalid_request", "subject_token_type parameter is required for Token Exchange Grant");
    }
  }

  void throwExceptionIfUnSupportedGrantTypeWithClient() {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.token_exchange)) {
      throw new TokenBadRequestException(
          "unauthorized_client",
          "this request grant_type is urn:ietf:params:oauth:grant-type:token-exchange, but client does not support");
    }
  }

  void throwExceptionIfUnSupportedGrantTypeWithServer() {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.token_exchange)) {
      throw new TokenBadRequestException(
          "unsupported_grant_type",
          "this request grant_type is urn:ietf:params:oauth:grant-type:token-exchange, but authorization server does not support");
    }
  }
}

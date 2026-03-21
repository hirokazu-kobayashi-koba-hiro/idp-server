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
 * <p>Validates token exchange request parameters per RFC 8693.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693</a>
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
  }

  void throwExceptionIfSubjectTokenNotProvided() {
    if (!tokenRequestContext.hasSubjectToken()) {
      throw new TokenBadRequestException(
          "invalid_request", "subject_token parameter is required for Token Exchange Grant");
    }
  }

  void throwExceptionIfSubjectTokenTypeNotProvided() {
    if (!tokenRequestContext.hasSubjectTokenType()) {
      throw new TokenBadRequestException(
          "invalid_request", "subject_token_type parameter is required for Token Exchange Grant");
    }
    if (!tokenRequestContext.subjectTokenType().exists()) {
      throw new TokenBadRequestException(
          "invalid_request", "subject_token_type parameter value is not supported");
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

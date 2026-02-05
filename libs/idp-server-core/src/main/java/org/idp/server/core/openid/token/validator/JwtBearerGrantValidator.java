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

public class JwtBearerGrantValidator {

  TokenRequestContext tokenRequestContext;

  public JwtBearerGrantValidator(TokenRequestContext tokenRequestContext) {
    this.tokenRequestContext = tokenRequestContext;
  }

  public void validate() {
    throwExceptionIfUnSupportedGrantTypeWithServer();
    throwExceptionIfUnSupportedGrantTypeWithClient();
    throwExceptionIfAssertionNotProvided();
  }

  /**
   * RFC 7523 Section 2.1 - assertion parameter is required
   *
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523#section-2.1">RFC 7523 Section
   *     2.1</a>
   */
  void throwExceptionIfAssertionNotProvided() {
    if (!tokenRequestContext.hasAssertion()) {
      throw new TokenBadRequestException(
          "invalid_request", "assertion parameter is required for JWT Bearer Grant");
    }
  }

  /**
   * 5.2. Error Response unauthorized_client
   *
   * <p>The authenticated client is not authorized to use this authorization grant type.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-5.2">5.2. Error Response</a>
   */
  void throwExceptionIfUnSupportedGrantTypeWithClient() {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.jwt_bearer)) {
      throw new TokenBadRequestException(
          "unauthorized_client",
          "this request grant_type is urn:ietf:params:oauth:grant-type:jwt-bearer, but client does not support");
    }
  }

  /**
   * 5.2. Error Response unsupported_grant_type
   *
   * <p>The authorization grant type is not supported by the authorization server.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-5.2">5.2. Error Response</a>
   */
  void throwExceptionIfUnSupportedGrantTypeWithServer() {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.jwt_bearer)) {
      throw new TokenBadRequestException(
          "unsupported_grant_type",
          "this request grant_type is urn:ietf:params:oauth:grant-type:jwt-bearer, but authorization server does not support");
    }
  }
}

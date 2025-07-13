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

package org.idp.server.core.oidc.token.validator;

import org.idp.server.core.oidc.token.TokenRequestContext;
import org.idp.server.core.oidc.token.exception.TokenBadRequestException;
import org.idp.server.core.oidc.type.oauth.GrantType;

public class RefreshTokenGrantValidator {

  TokenRequestContext tokenRequestContext;

  public RefreshTokenGrantValidator(TokenRequestContext tokenRequestContext) {
    this.tokenRequestContext = tokenRequestContext;
  }

  public void validate() {
    throwExceptionIfNotContainsRefreshToken();
    throwExceptionIfUnSupportedGrantTypeWithServer();
    throwExceptionIfUnSupportedGrantTypeWithClient();
    throwExceptionIfNotContainsClientId();
  }

  void throwExceptionIfUnSupportedGrantTypeWithClient() {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.refresh_token)) {
      throw new TokenBadRequestException(
          "unauthorized_client",
          "this request grant_type is refresh_token, but client does not support");
    }
  }

  void throwExceptionIfUnSupportedGrantTypeWithServer() {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.refresh_token)) {
      throw new TokenBadRequestException(
          "unsupported_grant_type",
          "this request grant_type is refresh_token, but authorization server does not support");
    }
  }

  void throwExceptionIfNotContainsRefreshToken() {
    if (!tokenRequestContext.hasRefreshToken()) {
      throw new TokenBadRequestException(
          "token request does not contains refresh_token, refresh_token grant must contains refresh_token");
    }
  }

  void throwExceptionIfNotContainsClientId() {
    if (tokenRequestContext.hasClientSecretBasic()) {
      return;
    }
    if (!tokenRequestContext.hasClientId()) {
      throw new TokenBadRequestException(
          "token request does not contains client_id, password must contains client_id");
    }
  }
}

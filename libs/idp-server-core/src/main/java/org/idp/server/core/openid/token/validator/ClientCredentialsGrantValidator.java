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

public class ClientCredentialsGrantValidator {

  TokenRequestContext tokenRequestContext;

  public ClientCredentialsGrantValidator(TokenRequestContext tokenRequestContext) {
    this.tokenRequestContext = tokenRequestContext;
  }

  public void validate() {
    throwExceptionIfUnSupportedGrantTypeWithServer();
    throwExceptionIfUnSupportedGrantTypeWithClient();
  }

  void throwExceptionIfUnSupportedGrantTypeWithClient() {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.password)) {
      throw new TokenBadRequestException(
          "unsupported_grant_type",
          "this request grant_type is password, but client does not support");
    }
    if (!tokenRequestContext.isSupportedPasswordGrant()) {
      throw new TokenBadRequestException(
          "unsupported_grant_type",
          "this request grant_type is client_credentials, but client does not support");
    }
  }

  void throwExceptionIfUnSupportedGrantTypeWithServer() {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.password)) {
      throw new TokenBadRequestException(
          "unauthorized_client",
          "this request grant_type is client_credentials, but authorization server does not authorize");
    }
  }
}

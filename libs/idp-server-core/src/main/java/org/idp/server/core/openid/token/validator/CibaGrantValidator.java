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

public class CibaGrantValidator {

  TokenRequestContext tokenRequestContext;

  public CibaGrantValidator(TokenRequestContext tokenRequestContext) {
    this.tokenRequestContext = tokenRequestContext;
  }

  public void validate() {
    throwExceptionIfUnSupportedGrantTypeWithServer();
    throwExceptionIfUnSupportedGrantTypeWithClient();
    throwExceptionIfNotContainsAuthReqId();
    // Note: client_id validation is not needed here because:
    // 1. TokenRequest.clientId() already extracts from parameters, Basic Auth, or client_assertion
    // 2. ClientConfiguration lookup would fail before reaching this validator if client_id is
    // missing
  }

  void throwExceptionIfUnSupportedGrantTypeWithClient() {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.ciba)) {
      throw new TokenBadRequestException(
          "unauthorized_client", "this request grant_type is ciba, but client does not authorize");
    }
  }

  void throwExceptionIfUnSupportedGrantTypeWithServer() {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.ciba)) {
      throw new TokenBadRequestException(
          "unsupported_grant_type",
          "this request grant_type is ciba, but authorization server does not support");
    }
  }

  void throwExceptionIfNotContainsAuthReqId() {
    if (!tokenRequestContext.hasAuthReqId()) {
      throw new TokenBadRequestException(
          "token request does not contains auth_req_id, ciba grant must contains auth_req_id");
    }
  }
}

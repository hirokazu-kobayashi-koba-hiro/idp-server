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

import org.idp.server.core.oidc.token.TokenRequestParameters;
import org.idp.server.core.oidc.token.exception.TokenBadRequestException;

public class TokenRequestCodeGrantValidator {
  TokenRequestParameters parameters;

  public TokenRequestCodeGrantValidator(TokenRequestParameters parameters) {
    this.parameters = parameters;
  }

  public void validate() {
    throwExceptionIfNotContainsAuthorizationCode();
  }

  void throwExceptionIfNotContainsAuthorizationCode() {
    if (!parameters.hasCode()) {
      throw new TokenBadRequestException(
          "token request does not contains code, authorization_code grant must contains code");
    }
  }
}

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

import java.util.List;
import org.idp.server.core.oidc.token.TokenRequestParameters;
import org.idp.server.core.oidc.token.exception.TokenBadRequestException;

public class TokenRequestValidator {
  TokenRequestParameters parameters;

  public TokenRequestValidator(TokenRequestParameters parameters) {
    this.parameters = parameters;
  }

  public void validate() {
    throwExceptionIfNotContainsGrantType();
    throwExceptionIfDuplicateValue();
  }

  void throwExceptionIfNotContainsGrantType() {
    if (!parameters.hasGrantType()) {
      throw new TokenBadRequestException(
          "token request must contains grant_type, but this request does not contains grant_type");
    }
  }

  /**
   * 3.2. Token Endpoint validation
   *
   * <p>Request and response parameters MUST NOT be included more than once.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-3.2">3.2. Authorization
   *     Endpoint</a>
   */
  void throwExceptionIfDuplicateValue() {
    List<String> keys = parameters.multiValueKeys();
    if (!keys.isEmpty()) {
      String keysValue = String.join(" ", keys);
      throw new TokenBadRequestException(
          String.format("token request must not contains duplicate value; keys (%s)", keysValue));
    }
  }
}

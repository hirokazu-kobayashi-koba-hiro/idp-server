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

package org.idp.server.core.extension.ciba.validator;

import java.util.Objects;
import org.idp.server.core.extension.ciba.exception.CibaAuthorizeBadRequestException;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.openid.authentication.Authentication;

public class CibaAuthorizeRequestValidator {
  BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier;
  Authentication authentication;

  public CibaAuthorizeRequestValidator(
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier,
      Authentication authentication) {
    this.backchannelAuthenticationRequestIdentifier = backchannelAuthenticationRequestIdentifier;
    this.authentication = authentication;
  }

  public void validate() {
    throwExceptionIfNotRequiredParameters();
  }

  void throwExceptionIfNotRequiredParameters() {
    if (Objects.isNull(backchannelAuthenticationRequestIdentifier)
        || !backchannelAuthenticationRequestIdentifier.exists()) {
      throw new CibaAuthorizeBadRequestException(
          "invalid_request", "backchannelAuthenticationRequestIdentifier is required");
    }

    if (Objects.isNull(authentication)) {
      throw new CibaAuthorizeBadRequestException("invalid_request", "authentication is required");
    }
  }
}

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

package org.idp.server.core.openid.oauth.validator;

import java.util.Objects;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.exception.OAuthAuthorizeBadRequestException;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.oauth.type.extension.CustomProperties;

public class OAuthAuthorizeRequestValidator {
  AuthorizationRequestIdentifier authorizationRequestIdentifier;
  User user;
  Authentication authentication;
  CustomProperties customProperties;

  public OAuthAuthorizeRequestValidator(
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      User user,
      Authentication authentication,
      CustomProperties customProperties) {
    this.authorizationRequestIdentifier = authorizationRequestIdentifier;
    this.user = user;
    this.authentication = authentication;
    this.customProperties = customProperties;
  }

  public void validate() {
    throwExceptionIfNotRequiredParameters();
    throwExceptionIfInvalidUserStatus();
  }

  void throwExceptionIfNotRequiredParameters() {
    if (Objects.isNull(authorizationRequestIdentifier)
        || !authorizationRequestIdentifier.exists()) {
      throw new OAuthAuthorizeBadRequestException(
          "invalid_request", "authorizationRequestIdentifier is required");
    }
    if (Objects.isNull(user) || !user.exists()) {
      throw new OAuthAuthorizeBadRequestException("invalid_request", "user is required");
    }
    if (Objects.isNull(authentication)) {
      throw new OAuthAuthorizeBadRequestException("invalid_request", "authentication is required");
    }
  }

  void throwExceptionIfInvalidUserStatus() {
    if (!user.isActive()) {
      throw new OAuthAuthorizeBadRequestException(
          "invalid_request",
          String.format("User status is not active, cannot authorize. (%s)", user.status().name()));
    }
  }
}

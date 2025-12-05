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

package org.idp.server.core.openid.token.verifier;

import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;

public class ResourceOwnerPasswordGrantVerifier {

  User user;
  Scopes scopes;

  public ResourceOwnerPasswordGrantVerifier(User user, Scopes scopes) {
    this.user = user;
    this.scopes = scopes;
  }

  public void verify() {
    throwExceptionIfUnspecifiedUser();
    throwExceptionIfInactiveUser();
    throwExceptionIfInvalidScope();
  }

  void throwExceptionIfUnspecifiedUser() {
    if (!user.exists()) {
      throw new TokenBadRequestException(
          "invalid_grant", "does not found user by token request, or invalid password");
    }
  }

  void throwExceptionIfInactiveUser() {
    if (!user.isActive()) {
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format(
              "user is not active (id: %s, status: %s)", user.sub(), user.status().name()));
    }
  }

  void throwExceptionIfInvalidScope() {
    if (!scopes.exists()) {
      throw new TokenBadRequestException(
          "invalid_scope", "token request does not contains valid scope");
    }
  }
}

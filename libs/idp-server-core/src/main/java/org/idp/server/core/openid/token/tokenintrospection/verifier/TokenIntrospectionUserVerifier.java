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

package org.idp.server.core.openid.token.tokenintrospection.verifier;

import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenUserInactiveException;

/**
 * Verifies user state during Token Introspection.
 *
 * <p>This class validates the user associated with a token during introspection requests. Per RFC
 * 7662, if the user is inactive, the token should be considered invalid and {@code active: false}
 * should be returned.
 *
 * <h2>Verification Items</h2>
 *
 * <ul>
 *   <li>User existence check - whether the user associated with the token exists
 *   <li>User status check - whether the user is in an active state
 * </ul>
 *
 * <h2>Inactive User Statuses</h2>
 *
 * <ul>
 *   <li>{@code LOCKED} - Account locked due to excessive authentication failures
 *   <li>{@code DISABLED} - Disabled by administrator
 *   <li>{@code SUSPENDED} - Temporarily suspended due to policy violation
 *   <li>{@code DEACTIVATED} - Deactivated by user request
 *   <li>{@code DELETED_PENDING} - Pending deletion
 *   <li>{@code DELETED} - Permanently deleted
 * </ul>
 *
 * <h2>Design Rationale</h2>
 *
 * <p>Token introspection is often used by resource servers to validate access tokens. If the user
 * has been disabled since the token was issued, the resource server should reject the request. This
 * follows the Keycloak approach where disabled users result in {@code active: false}.
 *
 * @see TokenIntrospectionVerifier Token validation (existence, expiration)
 * @see org.idp.server.core.openid.identity.UserStatus User status definitions
 */
public class TokenIntrospectionUserVerifier {

  User user;

  /**
   * Constructs a new verifier.
   *
   * @param user the user to verify
   */
  public TokenIntrospectionUserVerifier(User user) {
    this.user = user;
  }

  /**
   * Executes user verification.
   *
   * <p>Throws {@link TokenUserInactiveException} if the user does not exist or is inactive.
   *
   * @throws TokenUserInactiveException if user does not exist or is inactive
   */
  public void verify() {
    throwExceptionIfNotFoundUser();
    throwExceptionIfInactiveUser();
  }

  void throwExceptionIfNotFoundUser() {
    if (!user.exists()) {
      throw new TokenUserInactiveException("user not found");
    }
  }

  void throwExceptionIfInactiveUser() {
    if (!user.isActive()) {
      throw new TokenUserInactiveException(
          String.format(
              "user is not active (id: %s, status: %s)", user.sub(), user.status().name()));
    }
  }
}

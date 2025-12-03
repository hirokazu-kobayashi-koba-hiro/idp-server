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
import org.idp.server.core.openid.token.exception.TokenBadRequestException;

/**
 * Verifies user state during Refresh Token Grant.
 *
 * <p>This class validates the user associated with a refresh token during token refresh requests.
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
 * <p>Refresh tokens have long lifetimes, during which a user may become inactive. For security
 * purposes, re-validating user status during token refresh prevents access by disabled users.
 *
 * <p>This design follows the Keycloak approach.
 *
 * @see RefreshTokenVerifier Token validation (existence, expiration)
 * @see org.idp.server.core.openid.identity.UserStatus User status definitions
 */
public class RefreshTokenUserVerifier {

  User user;

  /**
   * Constructs a new verifier.
   *
   * @param user the user to verify
   */
  public RefreshTokenUserVerifier(User user) {
    this.user = user;
  }

  /**
   * Executes user verification.
   *
   * <p>Verification is performed in the following order:
   *
   * <ol>
   *   <li>User existence check
   *   <li>User status check
   * </ol>
   *
   * @throws TokenBadRequestException if user does not exist or is inactive
   */
  public void verify() {
    throwExceptionIfNotFoundUser();
    throwExceptionIfInactiveUser();
  }

  void throwExceptionIfNotFoundUser() {
    if (!user.exists()) {
      throw new TokenBadRequestException("invalid_grant", "not found user");
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
}

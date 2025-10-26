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

package org.idp.server.core.openid.identity;

import org.idp.server.core.openid.identity.exception.UserDuplicateException;
import org.idp.server.core.openid.identity.exception.UserValidationException;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Verifies business rules for user registration and updates.
 *
 * <p>This verifier checks tenant-scoped uniqueness constraints to prevent duplicate user identities
 * within the same tenant based on the configured identity policy.
 */
public class UserVerifier {

  UserQueryRepository userQueryRepository;

  public UserVerifier(UserQueryRepository userQueryRepository) {
    this.userQueryRepository = userQueryRepository;
  }

  /**
   * Verifies that a user can be registered without violating business rules.
   *
   * <p>Checks include:
   *
   * <ul>
   *   <li>Required field validation (preferred_username)
   *   <li>Preferred username uniqueness within the tenant
   * </ul>
   *
   * @param tenant the tenant context
   * @param user the user to verify
   * @throws UserValidationException if required fields are missing
   * @throws UserDuplicateException if the user's preferred_username already exists in the tenant
   */
  public void verify(Tenant tenant, User user) {
    throwExceptionIfPreferredUsernameRequired(user);
    throwExceptionIfDuplicatePreferredUsername(tenant, user);
  }

  /**
   * Verifies that required field preferred_username is set.
   *
   * @param user the user to verify
   * @throws UserValidationException if preferred_username is null or empty
   */
  void throwExceptionIfPreferredUsernameRequired(User user) {
    if (user.preferredUsername() == null || user.preferredUsername().isBlank()) {
      throw new UserValidationException("User preferred_username is required for registration");
    }
  }

  /**
   * Verifies that the user's preferred_username is unique within the tenant and provider.
   *
   * <p>The preferred_username field contains a normalized identifier based on the tenant's identity
   * policy (username, email, phone, or external_user_id). This field must be unique within the
   * tenant and provider scope to ensure proper user identity management.
   *
   * <p>Issue #729: Multiple IdPs (e.g., Google, GitHub) can use the same preferred_username (e.g.,
   * user@example.com) within the same tenant, as uniqueness is enforced per provider.
   *
   * @param tenant the tenant context
   * @param user the user to verify
   * @throws UserDuplicateException if a user with the same preferred_username already exists in the
   *     tenant and provider
   */
  void throwExceptionIfDuplicatePreferredUsername(Tenant tenant, User user) {
    User existingUser =
        userQueryRepository.findByPreferredUsername(
            tenant, user.providerId(), user.preferredUsername());

    if (existingUser.exists() && !existingUser.userIdentifier().equals(user.userIdentifier())) {
      throw new UserDuplicateException(
          String.format(
              "User with preferred_username '%s' already exists for provider '%s' in tenant '%s'",
              user.preferredUsername(), user.providerId(), tenant.identifier().value()));
    }
  }
}

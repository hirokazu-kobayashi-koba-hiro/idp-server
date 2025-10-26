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

package org.idp.server.control_plane.base.verifier;

import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserVerifier {
  UserQueryRepository userQueryRepository;

  public UserVerifier(UserQueryRepository userQueryRepository) {
    this.userQueryRepository = userQueryRepository;
  }

  public void verify(Tenant tenant, User user) {
    throwExceptionIfUserIdAlreadyExists(tenant, user);
    throwExceptionIfUserEmailAlreadyExists(tenant, user);
    throwExceptionIfPreferredUsernameNotSet(user);
  }

  void throwExceptionIfUserIdAlreadyExists(Tenant tenant, User user) {
    User byId = userQueryRepository.findById(tenant, user.userIdentifier());
    if (byId.exists()) {
      throw new InvalidRequestException("User id is already exists");
    }
  }

  void throwExceptionIfUserEmailAlreadyExists(Tenant tenant, User user) {
    // Skip email duplicate check if email is not provided
    // Issue #729: EMAIL_OR_EXTERNAL_USER_ID policy allows users without email
    if (user.email() == null || user.email().isBlank()) {
      return;
    }

    User byEmail = userQueryRepository.findByEmail(tenant, user.email(), user.providerId());
    if (byEmail.exists()) {
      throw new InvalidRequestException("User email is already exists");
    }
  }

  void throwExceptionIfPreferredUsernameNotSet(User user) {
    // Issue #729: Verify that preferred_username was successfully set by identity policy
    // This ensures the policy fallback mechanism worked correctly
    if (user.preferredUsername() == null || user.preferredUsername().isBlank()) {
      throw new InvalidRequestException(
          "User preferred_username could not be determined from tenant identity policy.Ensure required fields (email, name, phone_number, or external_user_id) are provided.");
    }
  }
}

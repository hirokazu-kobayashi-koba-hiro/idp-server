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

import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserRegistrator {

  UserQueryRepository userQueryRepository;
  UserCommandRepository userCommandRepository;
  UserVerifier userVerifier;

  public UserRegistrator(
      UserQueryRepository userQueryRepository, UserCommandRepository userCommandRepository) {
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.userVerifier = new UserVerifier(userQueryRepository);
  }

  public UserRegistrationResult registerOrUpdate(Tenant tenant, User user) {

    User existingUser = userQueryRepository.findById(tenant, user.userIdentifier());

    if (existingUser.exists()) {
      User updatedUser = existingUser.updateWith(user);
      applyIdentityPolicyIfNeeded(tenant, updatedUser);
      userCommandRepository.update(tenant, updatedUser);
      return new UserRegistrationResult(updatedUser, false);
    }

    // Apply identity policy to set preferred_username if not set
    applyIdentityPolicyIfNeeded(tenant, user);

    // Verify business rules before new user registration
    userVerifier.verify(tenant, user);

    if (user.status().isInitialized()) {
      user.setStatus(UserStatus.REGISTERED);
    }

    userCommandRepository.register(tenant, user);

    return new UserRegistrationResult(user, true);
  }

  /**
   * Applies tenant identity policy to recalculate preferred_username.
   *
   * <p>The preferred_username field is recalculated based on the tenant's identity policy
   * (username, email, phone, or external_user_id). According to OIDC Core specification,
   * preferred_username is mutable and can change over time (e.g., when email is updated).
   *
   * <p>Issue #729: Always recalculate preferred_username to ensure it reflects the latest user
   * attributes (email, phone, etc.) according to the tenant's policy.
   *
   * @param tenant the tenant context
   * @param user the user to apply policy to
   */
  private void applyIdentityPolicyIfNeeded(Tenant tenant, User user) {
    // Always recalculate preferred_username based on current user attributes
    // OIDC Core: preferred_username is mutable and can change over time
    user.applyIdentityPolicy(tenant.identityPolicyConfig());
  }
}

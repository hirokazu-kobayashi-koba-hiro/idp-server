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

  public User registerOrUpdate(Tenant tenant, User user) {

    User existingUser = userQueryRepository.findById(tenant, user.userIdentifier());

    if (existingUser.exists()) {
      applyIdentityPolicyIfNeeded(tenant, user);
      UserUpdater userUpdater = new UserUpdater(user, existingUser);
      User updatedUser = userUpdater.update();
      applyIdentityPolicyToExistingUserIfNeeded(tenant, updatedUser);
      userCommandRepository.update(tenant, updatedUser);
      return updatedUser;
    }

    // Apply identity policy to set preferred_username if not set
    applyIdentityPolicyIfNeeded(tenant, user);

    // Verify business rules before new user registration
    userVerifier.verify(tenant, user);

    if (user.status().isInitialized()) {
      user.setStatus(UserStatus.REGISTERED);
    }

    userCommandRepository.register(tenant, user);

    return user;
  }

  /**
   * Applies tenant identity policy to set preferred_username if not already set.
   *
   * <p>The preferred_username field is used as the tenant-scoped unique identifier. If it's not
   * set, this method extracts the appropriate value based on the tenant's identity policy
   * (username, email, phone, or external_user_id).
   *
   * @param tenant the tenant context
   * @param user the user to apply policy to
   */
  private void applyIdentityPolicyIfNeeded(Tenant tenant, User user) {
    if (user.preferredUsername() == null || user.preferredUsername().isBlank()) {
      TenantIdentityPolicy policy = TenantIdentityPolicy.fromTenantAttributes(tenant.attributes());
      user.applyIdentityPolicy(policy);
    }
  }

  /**
   * Applies tenant identity policy to existing user if preferred_username is missing.
   *
   * <p>This method is called after merging updates to ensure that existing users with null
   * preferred_username (from before migration V1_0_5) get the value populated based on tenant
   * policy.
   *
   * @param tenant the tenant context
   * @param user the existing user after update
   */
  private void applyIdentityPolicyToExistingUserIfNeeded(Tenant tenant, User user) {
    if (user.preferredUsername() == null || user.preferredUsername().isBlank()) {
      TenantIdentityPolicy policy = TenantIdentityPolicy.fromTenantAttributes(tenant.attributes());
      user.applyIdentityPolicy(policy);
    }
  }
}

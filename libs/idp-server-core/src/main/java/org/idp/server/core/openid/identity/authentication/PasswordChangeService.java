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

package org.idp.server.core.openid.identity.authentication;

import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.policy.PasswordPolicyConfig;

/**
 * Password change service.
 *
 * <p>Handles password change operations for authenticated users following industry best practices:
 *
 * <ol>
 *   <li>Validates request parameters (current and new password required)
 *   <li>Verifies current password against stored hash
 *   <li>Validates new password against tenant's password policy
 *   <li>Encodes new password and updates user
 * </ol>
 *
 * @see <a href="https://learn.microsoft.com/en-us/graph/api/user-changepassword">Microsoft Graph
 *     API: changePassword</a>
 * @see <a href="https://developer.okta.com/docs/reference/api/users/#change-password">Okta Users
 *     API: Change Password</a>
 */
public class PasswordChangeService {

  private final PasswordVerificationDelegation passwordVerificationDelegation;
  private final PasswordEncodeDelegation passwordEncodeDelegation;
  private final UserCommandRepository userCommandRepository;

  public PasswordChangeService(
      PasswordVerificationDelegation passwordVerificationDelegation,
      PasswordEncodeDelegation passwordEncodeDelegation,
      UserCommandRepository userCommandRepository) {
    this.passwordVerificationDelegation = passwordVerificationDelegation;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
    this.userCommandRepository = userCommandRepository;
  }

  /**
   * Changes user's password.
   *
   * @param tenant tenant
   * @param user authenticated user
   * @param request password change request
   * @return password change response
   */
  public PasswordChangeResponse changePassword(
      Tenant tenant, User user, PasswordChangeRequest request) {

    // 1. Validate request parameters
    if (!request.hasCurrentPassword()) {
      return PasswordChangeResponse.invalidRequest("Current password is required.");
    }
    if (!request.hasNewPassword()) {
      return PasswordChangeResponse.invalidRequest("New password is required.");
    }

    // 2. Verify current password
    if (!passwordVerificationDelegation.verify(request.currentPassword(), user.hashedPassword())) {
      return PasswordChangeResponse.invalidCurrentPassword();
    }

    // 3. Validate new password against tenant's policy
    PasswordPolicyConfig policyConfig = tenant.identityPolicyConfig().passwordPolicyConfig();
    PasswordPolicyValidator passwordPolicy = new PasswordPolicyValidator(policyConfig);
    PasswordPolicyValidationResult policyResult = passwordPolicy.validate(request.newPassword());
    if (policyResult.isInvalid()) {
      return PasswordChangeResponse.invalidNewPassword(policyResult.errorMessage());
    }

    // 4. Encode new password and update user
    String encodedPassword = passwordEncodeDelegation.encode(request.newPassword());
    user.setHashedPassword(encodedPassword);

    // 5. Save to database
    userCommandRepository.updatePassword(tenant, user);

    return PasswordChangeResponse.success(user);
  }
}

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

package org.idp.server.control_plane.management.identity.user.handler;

import java.util.Map;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.identity.user.ManagementEventPublisher;
import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.control_plane.management.identity.user.io.UserUpdateRequest;
import org.idp.server.control_plane.management.identity.user.validator.UserPasswordUpdateRequestValidator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.openid.identity.authentication.PasswordPolicyValidationResult;
import org.idp.server.core.openid.identity.authentication.PasswordPolicyValidator;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for updating user passwords.
 *
 * <p>Handles password update logic following the Handler/Service pattern. Password updates require
 * special handling including encoding and security event publishing.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>User existence verification
 *   <li>Password validation
 *   <li>Password encoding (via PasswordEncodeDelegation)
 *   <li>Password update in repository
 *   <li>Security event publishing
 * </ul>
 *
 * <h2>NOT Responsibilities (handled by UserManagementHandler)</h2>
 *
 * <ul>
 *   <li>Permission checking
 *   <li>Audit logging
 *   <li>Transaction management
 * </ul>
 */
public class UserPasswordUpdateService implements UserManagementService<UserUpdateRequest> {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(UserPasswordUpdateService.class);
  private final UserQueryRepository userQueryRepository;
  private final UserCommandRepository userCommandRepository;
  private final PasswordEncodeDelegation passwordEncodeDelegation;
  private final ManagementEventPublisher managementEventPublisher;

  public UserPasswordUpdateService(
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      PasswordEncodeDelegation passwordEncodeDelegation,
      ManagementEventPublisher managementEventPublisher) {
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
    this.managementEventPublisher = managementEventPublisher;
  }

  public String type() {
    return "user_update_password";
  }

  @Override
  public UserManagementResponse execute(
      UserManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      UserUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. User existence verification
    User before = userQueryRepository.get(tenant, request.userIdentifier());

    // 2. Password validation
    UserPasswordUpdateRequestValidator validator =
        new UserPasswordUpdateRequestValidator(request.registrationRequest(), dryRun);
    validator.validate();

    // 3. Password update context creation (with encoding)
    User after = updateUserPassword(tenant, request.registrationRequest(), before);

    // 4. Set before/after users to builder for context completion
    builder.withBefore(before);
    builder.withAfter(after);

    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> contents = Map.of("result", after.toMap(), "diff", diff, "dry_run", dryRun);
    UserManagementResponse response = new UserManagementResponse(UserManagementStatus.OK, contents);

    // 5. Dry-run check
    if (dryRun) {
      return response;
    }

    // 6. Repository operation (password-specific update)
    userCommandRepository.updatePassword(tenant, after);

    // 7. Re-fetch from DB to get complete user data
    User updatedUser = userQueryRepository.get(tenant, request.userIdentifier());

    // 8. Recalculate diff with actual DB state
    JsonNodeWrapper updatedJson = JsonNodeWrapper.fromMap(updatedUser.toMap());
    Map<String, Object> actualDiff = JsonDiffCalculator.deepDiff(beforeJson, updatedJson);
    Map<String, Object> actualContents =
        Map.of("result", updatedUser.toMap(), "diff", actualDiff, "dry_run", dryRun);

    // 9. Security event publishing (password change event)
    managementEventPublisher.publishSync(
        tenant,
        operator,
        updatedUser,
        oAuthToken,
        DefaultSecurityEventType.password_change.toEventType(),
        requestAttributes);

    return new UserManagementResponse(UserManagementStatus.OK, actualContents);
  }

  private User updateUserPassword(
      Tenant tenant, UserRegistrationRequest registrationRequest, User before) {
    User newUser = JsonConverter.snakeCaseInstance().read(registrationRequest.toMap(), User.class);

    // Validate password against tenant password policy
    // Note: Always validate when raw_password field exists (even if empty/null)
    // to ensure proper error messages for empty passwords
    log.debug("Applying tenant password policy for password update: user={}", before.sub());
    TenantIdentityPolicy policy = TenantIdentityPolicy.fromTenantAttributes(tenant.attributes());
    PasswordPolicyValidator passwordPolicy =
        new PasswordPolicyValidator(policy.passwordPolicyConfig());
    PasswordPolicyValidationResult validationResult =
        passwordPolicy.validate(newUser.rawPassword());
    if (validationResult.isInvalid()) {
      log.info(
          "Password update failed: password policy violation for user={} - {}",
          before.sub(),
          validationResult.errorMessage());
      throw new InvalidRequestException(
          "Password policy violation: " + validationResult.errorMessage());
    }
    log.debug("Password policy validation succeeded for password update: user={}", before.sub());

    String hashedPassword = passwordEncodeDelegation.encode(newUser.rawPassword());
    User updated = before.setHashedPassword(hashedPassword);

    // Apply tenant identity policy if preferred_username is not set
    if (updated.preferredUsername() == null || updated.preferredUsername().isBlank()) {
      updated.applyIdentityPolicy(policy);
    }

    return updated;
  }
}

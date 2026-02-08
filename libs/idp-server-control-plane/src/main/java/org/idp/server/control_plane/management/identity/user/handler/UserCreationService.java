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
import java.util.UUID;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.identity.user.ManagementEventPublisher;
import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.control_plane.management.identity.user.validator.UserRegistrationRequestValidator;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.openid.identity.authentication.PasswordPolicyValidationResult;
import org.idp.server.core.openid.identity.authentication.PasswordPolicyValidator;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for creating new users.
 *
 * <p>Handles user creation logic extracted from UserManagementEntryService. Focuses solely on the
 * business logic of user creation without cross-cutting concerns like permission checking or audit
 * logging.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Request validation
 *   <li>Context creation
 *   <li>Business rule verification
 *   <li>User registration in repository
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
public class UserCreationService implements UserManagementService<UserRegistrationRequest> {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(UserCreationService.class);
  private final UserCommandRepository userCommandRepository;
  private final PasswordEncodeDelegation passwordEncodeDelegation;
  private final UserRegistrationVerifier verifier;
  private final ManagementEventPublisher managementEventPublisher;

  public UserCreationService(
      UserCommandRepository userCommandRepository,
      PasswordEncodeDelegation passwordEncodeDelegation,
      UserRegistrationVerifier verifier,
      ManagementEventPublisher managementEventPublisher) {
    this.userCommandRepository = userCommandRepository;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
    this.verifier = verifier;
    this.managementEventPublisher = managementEventPublisher;
  }

  @Override
  public UserManagementResponse execute(
      UserManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Validation (throws InvalidRequestException if validation fails)
    new UserRegistrationRequestValidator(request, dryRun).validate();

    // 2. User creation
    User user = createUser(tenant, request);

    // 3. Set user to builder for context completion
    builder.withAfter(user);

    // 4. Business rule verification (throws exception if verification fails)
    verifier.verify(tenant, user, request);

    Map<String, Object> contents = Map.of("result", user.toMap(), "dry_run", dryRun);

    // 5. Dry-run check
    if (dryRun) {
      return new UserManagementResponse(UserManagementStatus.OK, contents);
    }

    // 6. Repository operation
    userCommandRepository.register(tenant, user);

    // 7. Security event publishing
    managementEventPublisher.publishSync(
        tenant,
        operator,
        user,
        oAuthToken,
        DefaultSecurityEventType.user_create.toEventType(),
        requestAttributes);

    return new UserManagementResponse(UserManagementStatus.CREATED, contents);
  }

  User createUser(Tenant tenant, UserRegistrationRequest request) {
    User user = JsonConverter.snakeCaseInstance().read(request.toMap(), User.class);

    // Generate sub if not provided
    if (!user.hasSub()) {
      user.setSub(UUID.randomUUID().toString());
    }
    if (!user.hasStatus()) {
      user.setStatus(UserStatus.REGISTERED);
    }

    // Always recalculate preferred_username based on tenant identity policy
    // OIDC Core: preferred_username is mutable and should reflect current user attributes
    // Issue #729: Always apply policy to ensure consistency with email/phone/username changes
    TenantIdentityPolicy policy = tenant.identityPolicyConfig();
    user.applyIdentityPolicy(policy);

    // Validate password against tenant password policy
    // Note: Always validate when raw_password field exists (even if empty/null)
    // to ensure proper error messages for empty passwords
    log.debug("Applying tenant password policy for user creation");
    PasswordPolicyValidator passwordPolicy =
        new PasswordPolicyValidator(policy.passwordPolicyConfig());
    PasswordPolicyValidationResult validationResult = passwordPolicy.validate(user.rawPassword());
    if (validationResult.isInvalid()) {
      log.info(
          "User creation failed: password policy violation - {}", validationResult.errorMessage());
      throw new InvalidRequestException(
          "Password policy violation: " + validationResult.errorMessage());
    }
    log.debug("Password policy validation succeeded for user creation");

    // Encode password
    String encoded = passwordEncodeDelegation.encode(user.rawPassword());
    user.setHashedPassword(encoded);
    user.setStatus(UserStatus.REGISTERED);

    return user;
  }
}

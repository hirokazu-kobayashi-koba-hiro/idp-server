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

import org.idp.server.control_plane.management.identity.user.ManagementEventPublisher;
import org.idp.server.control_plane.management.identity.user.UserRegistrationContext;
import org.idp.server.control_plane.management.identity.user.UserRegistrationContextCreator;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.control_plane.management.identity.user.validator.UserRegistrationRequestValidator;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
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
  public UserManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Validation (throws InvalidRequestException if validation fails)
    new UserRegistrationRequestValidator(request, dryRun).validate();

    // 2. Context creation
    UserRegistrationContextCreator contextCreator =
        new UserRegistrationContextCreator(tenant, request, dryRun, passwordEncodeDelegation);
    UserRegistrationContext context = contextCreator.create();

    // 3. Business rule verification (throws exception if verification fails)
    verifier.verify(context);

    // 4. Dry-run check
    if (dryRun) {
      return UserManagementResult.success(tenant, context, context.toResponse());
    }

    // 5. Repository operation
    userCommandRepository.register(tenant, context.user());

    // 6. Security event publishing
    managementEventPublisher.publish(
        tenant,
        operator,
        context.user(),
        oAuthToken,
        DefaultSecurityEventType.user_create.toEventType(),
        requestAttributes);

    return UserManagementResult.success(tenant, context, context.toResponse());
  }
}

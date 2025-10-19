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

import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.identity.user.ManagementEventPublisher;
import org.idp.server.control_plane.management.identity.user.UserUpdateContext;
import org.idp.server.control_plane.management.identity.user.UserUpdateContextCreator;
import org.idp.server.control_plane.management.identity.user.validator.UserRequestValidationResult;
import org.idp.server.control_plane.management.identity.user.validator.UserUpdateRequestValidator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for updating existing users.
 *
 * <p>Handles user update logic following the Handler/Service pattern. Focuses solely on the
 * business logic of user updates without cross-cutting concerns.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>User existence verification
 *   <li>Request validation
 *   <li>Context creation (before/after state)
 *   <li>User update in repository
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
public class UserUpdateService implements UserManagementService<UserUpdateRequest> {

  private final UserQueryRepository userQueryRepository;
  private final UserCommandRepository userCommandRepository;
  private final ManagementEventPublisher managementEventPublisher;

  public UserUpdateService(
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      ManagementEventPublisher managementEventPublisher) {
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.managementEventPublisher = managementEventPublisher;
  }

  @Override
  public UserManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      UserUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. User existence verification
    User before = userQueryRepository.findById(tenant, request.userIdentifier());
    if (!before.exists()) {
      throw new ResourceNotFoundException("User not found: " + request.userIdentifier().value());
    }

    // 2. Validation (throws InvalidRequestException if validation fails)
    UserUpdateRequestValidator validator =
        new UserUpdateRequestValidator(request.registrationRequest(), dryRun);
    UserRequestValidationResult validate = validator.validate();
    if (!validate.isValid()) {
      // Validator returns response instead of throwing - need to throw for Handler pattern
      throw validate.toException();
    }

    // 3. Context creation (before/after state)
    UserUpdateContextCreator contextCreator =
        new UserUpdateContextCreator(tenant, before, request.registrationRequest(), dryRun);
    UserUpdateContext context = contextCreator.create();

    // 4. Dry-run check
    if (dryRun) {
      return UserManagementResult.success(tenant, context, context.toResponse());
    }

    // 5. Repository operation
    userCommandRepository.update(tenant, context.after());

    // 6. Security event publishing
    managementEventPublisher.publish(
        tenant,
        operator,
        context.after(),
        oAuthToken,
        DefaultSecurityEventType.user_edit.toEventType(),
        requestAttributes);

    return UserManagementResult.success(tenant, context, context.toResponse());
  }
}

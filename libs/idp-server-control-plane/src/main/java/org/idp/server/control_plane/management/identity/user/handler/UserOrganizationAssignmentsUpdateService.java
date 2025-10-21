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

import org.idp.server.control_plane.management.identity.user.UserOrganizationAssignmentsUpdateContextCreator;
import org.idp.server.control_plane.management.identity.user.UserUpdateContext;
import org.idp.server.control_plane.management.identity.user.validator.UserOrganizationAssignmentsUpdateRequestValidator;
import org.idp.server.control_plane.management.identity.user.validator.UserRequestValidationResult;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for updating user organization assignments.
 *
 * <p>Handles user organization assignments update logic following the Handler/Service pattern. This
 * operation is specific to organization-level APIs.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>User existence verification
 *   <li>Request validation
 *   <li>Context creation
 *   <li>User update in repository
 * </ul>
 *
 * <h2>NOT Responsibilities (handled by Handler/EntryService)</h2>
 *
 * <ul>
 *   <li>Permission checking
 *   <li>Organization access control
 *   <li>Audit logging
 *   <li>Transaction management
 * </ul>
 */
public class UserOrganizationAssignmentsUpdateService
    implements UserManagementService<UserUpdateRequest> {

  private final UserQueryRepository userQueryRepository;
  private final UserCommandRepository userCommandRepository;

  public UserOrganizationAssignmentsUpdateService(
      UserQueryRepository userQueryRepository, UserCommandRepository userCommandRepository) {
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
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
    User before = userQueryRepository.get(tenant, request.userIdentifier());

    // 2. Validation
    UserOrganizationAssignmentsUpdateRequestValidator validator =
        new UserOrganizationAssignmentsUpdateRequestValidator(
            request.registrationRequest(), dryRun);
    UserRequestValidationResult validate = validator.validate();
    if (!validate.isValid()) {
      throw validate.toException();
    }

    // 3. Context creation
    UserOrganizationAssignmentsUpdateContextCreator contextCreator =
        new UserOrganizationAssignmentsUpdateContextCreator(
            tenant, before, request.registrationRequest(), dryRun);
    UserUpdateContext context = contextCreator.create();

    // 4. Dry-run check
    if (dryRun) {
      return UserManagementResult.success(tenant, context, context.toResponse());
    }

    // 5. Repository operation
    userCommandRepository.update(tenant, context.after());

    return UserManagementResult.success(tenant, context, context.toResponse());
  }
}

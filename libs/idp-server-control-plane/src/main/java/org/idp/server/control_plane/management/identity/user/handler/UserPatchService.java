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
import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.control_plane.management.identity.user.UserPatchContextCreator;
import org.idp.server.control_plane.management.identity.user.UserUpdateContext;
import org.idp.server.control_plane.management.identity.user.UserUpdateContextBuilder;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.validator.UserRequestValidationResult;
import org.idp.server.control_plane.management.identity.user.validator.UserUpdateRequestValidator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for patching user data (partial update).
 *
 * <p>Handles user patch logic following the Handler/Service pattern. Patch differs from update in
 * that it only modifies provided fields instead of replacing the entire resource.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>User existence verification
 *   <li>Request validation
 *   <li>Patch context creation (merges existing data with updates)
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
public class UserPatchService implements UserManagementService<UserUpdateRequest> {

  private final UserQueryRepository userQueryRepository;
  private final UserCommandRepository userCommandRepository;
  private final ManagementEventPublisher managementEventPublisher;

  public UserPatchService(
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      ManagementEventPublisher managementEventPublisher) {
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.managementEventPublisher = managementEventPublisher;
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

    // Cast to specific builder type
    UserUpdateContextBuilder updateBuilder = (UserUpdateContextBuilder) builder;

    // 1. User existence verification
    User before = userQueryRepository.get(tenant, request.userIdentifier());

    // 2. Validation
    UserUpdateRequestValidator validator =
        new UserUpdateRequestValidator(request.registrationRequest(), dryRun);
    UserRequestValidationResult validate = validator.validate();
    if (!validate.isValid()) {
      throw validate.toException();
    }

    // 3. Patch context creation (merges existing + updates)
    UserPatchContextCreator patchContextCreator =
        new UserPatchContextCreator(
            tenant, operator, oAuthToken, requestAttributes, before,
            request.registrationRequest(), dryRun);
    UserUpdateContext context = patchContextCreator.create();

    // 4. Set before/after users to builder for context completion
    updateBuilder.withBefore(context.beforeUser());
    updateBuilder.withAfter(context.afterUser());

    // 5. Dry-run check
    if (dryRun) {
      return context.toResponse();
    }

    // 6. Repository operation
    userCommandRepository.update(tenant, context.afterUser());

    // 7. Security event publishing
    managementEventPublisher.publish(
        tenant,
        operator,
        context.afterUser(),
        oAuthToken,
        DefaultSecurityEventType.user_edit.toEventType(),
        requestAttributes);

    return context.toResponse();
  }

  @Override
  public UserManagementContextBuilder createContextBuilder(
      TenantIdentifier tenantIdentifier,
      OrganizationIdentifier organizationIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      UserUpdateRequest request,
      boolean dryRun) {
    return new UserUpdateContextBuilder(
            tenantIdentifier, organizationIdentifier, operator, oAuthToken, requestAttributes)
        .withRequestPayload(request.registrationRequest().toMap())
        .withDryRun(dryRun);
  }
}

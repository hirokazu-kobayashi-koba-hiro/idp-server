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
import org.idp.server.control_plane.management.identity.user.ManagementEventPublisher;
import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.control_plane.management.identity.user.io.UserUpdateRequest;
import org.idp.server.control_plane.management.identity.user.validator.UserUpdateRequestValidator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;
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

    // 2. Validation (throws InvalidRequestException if validation fails)
    UserUpdateRequestValidator validator =
        new UserUpdateRequestValidator(request.registrationRequest(), dryRun);
    validator.validate();

    // 3. Context creation (before/after state)
    User after = updateUser(tenant, request.registrationRequest(), before);

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

    // 6. Repository operation
    userCommandRepository.update(tenant, after);

    // 7. Security event publishing
    managementEventPublisher.publish(
        tenant,
        operator,
        after,
        oAuthToken,
        DefaultSecurityEventType.user_edit.toEventType(),
        requestAttributes);

    return response;
  }

  private User updateUser(Tenant tenant, UserRegistrationRequest request, User before) {
    User newUser = JsonConverter.snakeCaseInstance().read(request.toMap(), User.class);
    newUser.setSub(before.sub());

    // Apply tenant identity policy to set preferred_username if not set
    if (newUser.preferredUsername() == null || newUser.preferredUsername().isBlank()) {
      // First try to preserve existing preferred_username
      if (before.hasPreferredUsername()) {
        newUser.setPreferredUsername(before.preferredUsername());
      } else {
        // If before also doesn't have it, apply policy
        TenantIdentityPolicy policy =
            TenantIdentityPolicy.fromTenantAttributes(tenant.attributes());
        newUser.applyIdentityPolicy(policy);
      }
    }

    return newUser;
  }
}

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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.identity.user.ManagementEventPublisher;
import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.event.UserLifecycleEvent;
import org.idp.server.core.openid.identity.event.UserLifecycleEventPublisher;
import org.idp.server.core.openid.identity.event.UserLifecycleType;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for deleting users.
 *
 * <p>Handles user deletion logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>User existence verification
 *   <li>User deletion from repository
 *   <li>Lifecycle event publishing
 * </ul>
 *
 * <h2>NOT Responsibilities (handled by UserManagementHandler)</h2>
 *
 * <ul>
 *   <li>Permission checking
 *   <li>Audit logging (done in EntryService)
 *   <li>Transaction management
 * </ul>
 */
public class UserDeletionService implements UserManagementService<UserIdentifier> {

  private final UserQueryRepository userQueryRepository;
  private final UserCommandRepository userCommandRepository;
  private final UserLifecycleEventPublisher userLifecycleEventPublisher;
  private final ManagementEventPublisher managementEventPublisher;

  public UserDeletionService(
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      UserLifecycleEventPublisher userLifecycleEventPublisher,
      ManagementEventPublisher managementEventPublisher) {
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.userLifecycleEventPublisher = userLifecycleEventPublisher;
    this.managementEventPublisher = managementEventPublisher;
  }

  @Override
  public UserManagementResponse execute(
      UserManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Cast to specific builder type
    UserDeletionContextBuilder deletionBuilder = (UserDeletionContextBuilder) builder;

    // 1. User existence verification
    User user = userQueryRepository.get(tenant, userIdentifier);

    // 2. Set user to builder for context completion
    deletionBuilder.withUser(user);

    // 3. Dry-run check
    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Deletion simulated successfully");
      response.put("sub", user.sub());
      response.put("dry_run", true);
      return new UserManagementResponse(UserManagementStatus.OK, response);
    }

    // 4. Repository operation
    userCommandRepository.delete(tenant, userIdentifier);

    // 5. Lifecycle event publishing
    UserLifecycleEvent userLifecycleEvent =
        new UserLifecycleEvent(tenant, user, UserLifecycleType.DELETE);
    userLifecycleEventPublisher.publish(userLifecycleEvent);

    // 6. Security event publishing
    managementEventPublisher.publish(
        tenant,
        operator,
        user,
        oAuthToken,
        DefaultSecurityEventType.user_delete.toEventType(),
        requestAttributes);

    // 7. Success response
    Map<String, Object> response = new HashMap<>();
    response.put("message", "User deleted successfully");
    response.put("sub", user.sub());
    return new UserManagementResponse(UserManagementStatus.NO_CONTENT, response);
  }

  @Override
  public UserManagementContextBuilder createContextBuilder(
      TenantIdentifier tenantIdentifier,
      OrganizationIdentifier organizationIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      UserIdentifier request,
      boolean dryRun) {
    return new UserDeletionContextBuilder(
            tenantIdentifier, organizationIdentifier, operator, oAuthToken, requestAttributes)
        .withDryRun(dryRun);
  }
}

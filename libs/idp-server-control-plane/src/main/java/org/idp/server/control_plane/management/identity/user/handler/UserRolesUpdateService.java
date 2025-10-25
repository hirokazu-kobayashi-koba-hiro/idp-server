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
import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.control_plane.management.identity.user.io.UserUpdateRequest;
import org.idp.server.control_plane.management.identity.user.validator.UserRolesUpdateRequestValidator;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationRelatedDataVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for updating user roles.
 *
 * <p>Handles user roles update logic following the Handler/Service pattern. This operation is
 * specific to organization-level APIs.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>User existence verification
 *   <li>Request validation
 *   <li>Role existence verification
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
public class UserRolesUpdateService implements UserManagementService<UserUpdateRequest> {

  private final UserQueryRepository userQueryRepository;
  private final UserCommandRepository userCommandRepository;
  private final UserRegistrationRelatedDataVerifier relatedDataVerifier;

  public UserRolesUpdateService(
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      UserRegistrationRelatedDataVerifier relatedDataVerifier) {
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.relatedDataVerifier = relatedDataVerifier;
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

    User before = userQueryRepository.get(tenant, request.userIdentifier());

    new UserRolesUpdateRequestValidator(request.registrationRequest(), dryRun).validate();
    relatedDataVerifier.verifyRoles(tenant, request.registrationRequest());

    User after = updateUser(request.registrationRequest(), before);

    // Set before/after users to builder for context completion
    builder.withBefore(before);
    builder.withAfter(after);

    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> contents = Map.of("result", after.toMap(), "diff", diff, "dry_run", dryRun);
    UserManagementResponse response = new UserManagementResponse(UserManagementStatus.OK, contents);

    if (dryRun) {
      return response;
    }

    userCommandRepository.update(tenant, after);
    return response;
  }

  public User updateUser(UserRegistrationRequest request, User before) {
    User newUser = JsonConverter.snakeCaseInstance().read(request.toMap(), User.class);

    return before.setRoles(newUser.roles());
  }
}

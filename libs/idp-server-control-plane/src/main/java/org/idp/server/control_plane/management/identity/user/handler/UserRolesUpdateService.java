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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.control_plane.management.identity.user.io.UserUpdateRequest;
import org.idp.server.control_plane.management.identity.user.validator.UserRolesUpdateRequestValidator;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationRelatedDataVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserRole;
import org.idp.server.core.openid.identity.permission.Permission;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.RoleIdentifier;
import org.idp.server.core.openid.identity.role.RoleQueryRepository;
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
  private final RoleQueryRepository roleQueryRepository;
  private final UserRegistrationRelatedDataVerifier relatedDataVerifier;

  public UserRolesUpdateService(
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      RoleQueryRepository roleQueryRepository,
      UserRegistrationRelatedDataVerifier relatedDataVerifier) {
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.roleQueryRepository = roleQueryRepository;
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

    User updated = updateUser(request.registrationRequest(), before);

    if (dryRun) {
      // For dry run, calculate permissions from roles to provide accurate preview
      List<String> permissions = calculatePermissionsFromRoles(tenant, updated.roles());
      updated.setPermissions(permissions);

      builder.withBefore(before);
      builder.withAfter(updated);

      JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
      JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(updated.toMap());
      Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
      Map<String, Object> contents =
          Map.of("result", updated.toMap(), "diff", diff, "dry_run", dryRun);
      return new UserManagementResponse(UserManagementStatus.OK, contents);
    }

    userCommandRepository.updateRoles(tenant, updated);

    // Re-fetch from DB to get accurate permissions derived from roles
    User after = userQueryRepository.get(tenant, request.userIdentifier());

    builder.withBefore(before);
    builder.withAfter(after);

    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> contents = Map.of("result", after.toMap(), "diff", diff, "dry_run", dryRun);
    return new UserManagementResponse(UserManagementStatus.OK, contents);
  }

  public User updateUser(UserRegistrationRequest request, User before) {
    // Create deep copy to avoid mutating 'before' object
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    User updated = jsonConverter.read(jsonConverter.write(before), User.class);

    User newUser = jsonConverter.read(request.toMap(), User.class);
    updated.setRoles(newUser.roles());

    return updated;
  }

  private List<String> calculatePermissionsFromRoles(Tenant tenant, List<UserRole> userRoles) {
    if (userRoles == null || userRoles.isEmpty()) {
      return new ArrayList<>();
    }

    Set<String> permissionNames = new LinkedHashSet<>();
    for (UserRole userRole : userRoles) {
      Role role = roleQueryRepository.find(tenant, new RoleIdentifier(userRole.roleId()));
      if (role != null && role.exists() && role.permissions() != null) {
        for (Permission permission : role.permissions()) {
          permissionNames.add(permission.name());
        }
      }
    }

    return new ArrayList<>(permissionNames);
  }
}

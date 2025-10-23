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

package org.idp.server.control_plane.management.role.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.role.RoleManagementContextBuilder;
import org.idp.server.control_plane.management.role.io.RoleManagementResponse;
import org.idp.server.control_plane.management.role.io.RoleManagementStatus;
import org.idp.server.control_plane.management.role.io.RoleRemovePermissionsRequest;
import org.idp.server.control_plane.management.role.io.RoleRequest;
import org.idp.server.control_plane.management.role.validator.RoleRemovePermissionsRequestValidator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.permission.PermissionQueryRepository;
import org.idp.server.core.openid.identity.permission.Permissions;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.RoleCommandRepository;
import org.idp.server.core.openid.identity.role.RoleQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for removing permissions from roles.
 *
 * <p>Handles permission removal logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Request validation via RoleRemovePermissionsRequestValidator (throws
 *       InvalidRequestException)
 *   <li>Existing role retrieval
 *   <li>Context creation via RoleRemovePermissionContextCreator
 *   <li>Permission removal (or dry-run simulation)
 * </ul>
 */
public class RoleRemovePermissionsService
    implements RoleManagementService<RoleRemovePermissionsRequest> {

  private final RoleQueryRepository roleQueryRepository;
  private final RoleCommandRepository roleCommandRepository;
  private final PermissionQueryRepository permissionQueryRepository;

  public RoleRemovePermissionsService(
      RoleQueryRepository roleQueryRepository,
      RoleCommandRepository roleCommandRepository,
      PermissionQueryRepository permissionQueryRepository) {
    this.roleQueryRepository = roleQueryRepository;
    this.roleCommandRepository = roleCommandRepository;
    this.permissionQueryRepository = permissionQueryRepository;
  }

  @Override
  public RoleManagementResponse execute(
      RoleManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      RoleRemovePermissionsRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Retrieve existing role (throws ResourceNotFoundException if not found)
    Role before = roleQueryRepository.find(tenant, request.identifier());

    if (!before.exists()) {
      throw new ResourceNotFoundException(
          String.format("Role not found: %s", request.identifier().value()));
    }

    // 2. Request validation
    new RoleRemovePermissionsRequestValidator(request.roleRequest(), dryRun).validate();

    // 3. Calculate removed permissions and create updated role
    Permissions allPermissions = permissionQueryRepository.findAll(tenant);
    Permissions removedPermissions =
        allPermissions.filterByName(request.roleRequest().permissions());
    Role after = removePermissions(before, request.roleRequest(), removedPermissions);

    // 4. Populate builder with before and after roles
    builder.withBefore(before);
    builder.withAfter(after);

    // 5. Build response
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> contents = new HashMap<>();
    contents.put("result", after.toMap());
    contents.put("diff", JsonDiffCalculator.deepDiff(beforeJson, afterJson));
    contents.put("dry_run", dryRun);

    if (dryRun) {
      return new RoleManagementResponse(RoleManagementStatus.OK, contents);
    }

    // 6. Repository operation
    roleCommandRepository.removePermissions(tenant, after, removedPermissions);

    return new RoleManagementResponse(RoleManagementStatus.OK, contents);
  }

  private Role removePermissions(Role before, RoleRequest request, Permissions removedPermissions) {
    String id = before.id();
    String name = request.name();
    String description = request.description();

    return new Role(id, name, description, removedPermissions.toList());
  }
}

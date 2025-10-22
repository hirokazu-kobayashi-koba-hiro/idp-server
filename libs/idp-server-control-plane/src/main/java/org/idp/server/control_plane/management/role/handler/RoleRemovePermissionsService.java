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

import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.role.RoleRemovePermissionContext;
import org.idp.server.control_plane.management.role.RoleRemovePermissionContextCreator;
import org.idp.server.control_plane.management.role.validator.RoleRemovePermissionsRequestValidator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.permission.PermissionQueryRepository;
import org.idp.server.core.openid.identity.permission.Permissions;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.RoleCommandRepository;
import org.idp.server.core.openid.identity.role.RoleQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
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
  public RoleManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      RoleRemovePermissionsRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Role before = roleQueryRepository.find(tenant, request.identifier());

    if (!before.exists()) {
      throw new ResourceNotFoundException(
          String.format("Role not found: %s", request.identifier().value()));
    }

    new RoleRemovePermissionsRequestValidator(request.roleRequest(), dryRun).validate();

    Permissions permissionList = permissionQueryRepository.findAll(tenant);
    RoleRemovePermissionContextCreator creator =
        new RoleRemovePermissionContextCreator(
            tenant, before, request.roleRequest(), permissionList, dryRun);
    RoleRemovePermissionContext context = creator.create();

    if (!dryRun) {
      roleCommandRepository.removePermissions(
          tenant, context.afterRole(), context.removedPermissions());
    }

    return RoleManagementResult.success(tenant, context.toResponse(), context);
  }
}

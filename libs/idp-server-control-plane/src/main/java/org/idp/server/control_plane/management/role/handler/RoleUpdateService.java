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
import org.idp.server.control_plane.management.role.RoleUpdateContext;
import org.idp.server.control_plane.management.role.RoleUpdateContextCreator;
import org.idp.server.control_plane.management.role.validator.RoleRequestValidator;
import org.idp.server.control_plane.management.role.verifier.RoleRegistrationVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.permission.PermissionQueryRepository;
import org.idp.server.core.openid.identity.permission.Permissions;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.RoleCommandRepository;
import org.idp.server.core.openid.identity.role.RoleQueryRepository;
import org.idp.server.core.openid.identity.role.Roles;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for updating roles.
 *
 * <p>Handles role update logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Request validation via RoleRequestValidator (throws InvalidRequestException)
 *   <li>Existing role retrieval
 *   <li>Context creation via RoleUpdateContextCreator
 *   <li>Business rule verification via RoleRegistrationVerifier (throws InvalidRequestException)
 *   <li>Role update (or dry-run simulation)
 * </ul>
 */
public class RoleUpdateService implements RoleManagementService<RoleUpdateRequest> {

  private final RoleQueryRepository roleQueryRepository;
  private final RoleCommandRepository roleCommandRepository;
  private final PermissionQueryRepository permissionQueryRepository;

  public RoleUpdateService(
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
      RoleUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Role before = roleQueryRepository.find(tenant, request.identifier());

    if (!before.exists()) {
      throw new ResourceNotFoundException(
          String.format("Role not found: %s", request.identifier().value()));
    }

    new RoleRequestValidator(request.roleRequest(), dryRun).validate();

    Roles roles = roleQueryRepository.findAll(tenant);
    Permissions permissionList = permissionQueryRepository.findAll(tenant);
    RoleUpdateContextCreator creator =
        new RoleUpdateContextCreator(
            tenant, before, request.roleRequest(), roles, permissionList, dryRun);
    RoleUpdateContext context = creator.create();

    new RoleRegistrationVerifier().verify(context);

    if (!dryRun) {
      roleCommandRepository.update(tenant, context.afterRole());
    }

    return RoleManagementResult.success(tenant, context.toResponse(), context);
  }
}

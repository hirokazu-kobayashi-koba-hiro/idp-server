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

import org.idp.server.control_plane.management.role.RoleRegistrationContext;
import org.idp.server.control_plane.management.role.RoleRegistrationContextCreator;
import org.idp.server.control_plane.management.role.io.RoleRequest;
import org.idp.server.control_plane.management.role.validator.RoleRequestValidator;
import org.idp.server.control_plane.management.role.verifier.RoleRegistrationVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.permission.PermissionQueryRepository;
import org.idp.server.core.openid.identity.permission.Permissions;
import org.idp.server.core.openid.identity.role.RoleCommandRepository;
import org.idp.server.core.openid.identity.role.RoleQueryRepository;
import org.idp.server.core.openid.identity.role.Roles;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for creating roles.
 *
 * <p>Handles role creation logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Request validation via RoleRequestValidator (throws InvalidRequestException)
 *   <li>Context creation via RoleRegistrationContextCreator
 *   <li>Business rule verification via RoleRegistrationVerifier (throws InvalidRequestException)
 *   <li>Role registration (or dry-run simulation)
 * </ul>
 */
public class RoleCreateService implements RoleManagementService<RoleRequest> {

  private final RoleQueryRepository roleQueryRepository;
  private final RoleCommandRepository roleCommandRepository;
  private final PermissionQueryRepository permissionQueryRepository;

  public RoleCreateService(
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
      RoleRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    new RoleRequestValidator(request, dryRun).validate();

    Roles roles = roleQueryRepository.findAll(tenant);
    Permissions permissionList = permissionQueryRepository.findAll(tenant);
    RoleRegistrationContextCreator creator =
        new RoleRegistrationContextCreator(tenant, request, roles, permissionList, dryRun);
    RoleRegistrationContext context = creator.create();

    new RoleRegistrationVerifier().verify(context);

    if (!dryRun) {
      roleCommandRepository.register(tenant, context.role());
    }

    return RoleManagementResult.success(tenant, context.toResponse(), context);
  }
}

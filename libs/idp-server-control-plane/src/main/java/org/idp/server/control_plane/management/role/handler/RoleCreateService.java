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

import java.util.Map;
import java.util.UUID;
import org.idp.server.control_plane.management.role.RoleManagementContextBuilder;
import org.idp.server.control_plane.management.role.io.RoleManagementResponse;
import org.idp.server.control_plane.management.role.io.RoleManagementStatus;
import org.idp.server.control_plane.management.role.io.RoleRequest;
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
  public RoleManagementResponse execute(
      RoleManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      RoleRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Request validation
    new RoleRequestValidator(request, dryRun).validate();

    // 2. Create role from request
    Roles roles = roleQueryRepository.findAll(tenant);
    Permissions permissions = permissionQueryRepository.findAll(tenant);
    Role role = createRole(request, permissions);

    // 3. Business rule verification
    new RoleRegistrationVerifier(request, roles, permissions).verify();

    // 4. Populate builder with created role
    builder.withAfter(role);

    // 5. Build response
    Map<String, Object> contents = Map.of("result", role.toMap(), "dry_run", dryRun);

    if (dryRun) {
      return new RoleManagementResponse(RoleManagementStatus.OK, contents);
    }

    // 6. Repository operation
    roleCommandRepository.register(tenant, role);

    return new RoleManagementResponse(RoleManagementStatus.CREATED, contents);
  }

  private Role createRole(RoleRequest request, Permissions permissions) {
    String id = request.hasId() ? request.id() : UUID.randomUUID().toString();
    String name = request.name();
    String description = request.description();

    Permissions filtered = permissions.filterById(request.permissions());

    return new Role(id, name, description, filtered.toList());
  }
}

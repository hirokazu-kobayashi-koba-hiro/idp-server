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

package org.idp.server.control_plane.management.permission.handler;

import java.util.Map;
import java.util.UUID;
import org.idp.server.control_plane.management.permission.PermissionManagementContextBuilder;
import org.idp.server.control_plane.management.permission.io.PermissionManagementResponse;
import org.idp.server.control_plane.management.permission.io.PermissionManagementStatus;
import org.idp.server.control_plane.management.permission.io.PermissionRequest;
import org.idp.server.control_plane.management.permission.validator.PermissionRequestValidator;
import org.idp.server.control_plane.management.permission.verifier.PermissionRegistrationVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.permission.Permission;
import org.idp.server.core.openid.identity.permission.PermissionCommandRepository;
import org.idp.server.core.openid.identity.permission.PermissionQueryRepository;
import org.idp.server.core.openid.identity.permission.Permissions;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for creating permissions.
 *
 * <p>Handles permission creation logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Request validation via PermissionRequestValidator (throws InvalidRequestException)
 *   <li>Permission object creation from request
 *   <li>Business rule verification via PermissionRegistrationVerifier (throws
 *       InvalidRequestException)
 *   <li>Permission registration (or dry-run simulation)
 * </ul>
 */
public class PermissionCreateService implements PermissionManagementService<PermissionRequest> {

  private final PermissionQueryRepository permissionQueryRepository;
  private final PermissionCommandRepository permissionCommandRepository;

  public PermissionCreateService(
      PermissionQueryRepository permissionQueryRepository,
      PermissionCommandRepository permissionCommandRepository) {
    this.permissionQueryRepository = permissionQueryRepository;
    this.permissionCommandRepository = permissionCommandRepository;
  }

  @Override
  public PermissionManagementResponse execute(
      PermissionManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      PermissionRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Request validation
    new PermissionRequestValidator(request, dryRun).validate();

    // 2. Create permission from request
    Permissions existingPermissions = permissionQueryRepository.findAll(tenant);
    Permission permission = createPermission(request);

    // 3. Business rule verification
    new PermissionRegistrationVerifier(request, existingPermissions).verify();

    // 4. Populate builder with created permission
    builder.withAfter(permission);

    // 5. Build response
    Map<String, Object> contents = Map.of("result", permission.toMap(), "dry_run", dryRun);

    if (dryRun) {
      return new PermissionManagementResponse(PermissionManagementStatus.OK, contents);
    }

    // 6. Repository operation
    permissionCommandRepository.register(tenant, permission);

    return new PermissionManagementResponse(PermissionManagementStatus.CREATED, contents);
  }

  private Permission createPermission(PermissionRequest request) {
    JsonNodeWrapper requestJson = JsonNodeWrapper.fromMap(request.toMap());
    String id =
        requestJson.contains("id")
            ? requestJson.getValueOrEmptyAsString("id")
            : UUID.randomUUID().toString();
    String name = requestJson.getValueOrEmptyAsString("name");
    String description = requestJson.getValueOrEmptyAsString("description");

    return new Permission(id, name, description);
  }
}

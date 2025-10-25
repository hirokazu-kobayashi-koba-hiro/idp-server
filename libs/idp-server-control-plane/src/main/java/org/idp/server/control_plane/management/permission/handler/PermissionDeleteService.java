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
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.permission.PermissionManagementContextBuilder;
import org.idp.server.control_plane.management.permission.io.PermissionDeleteRequest;
import org.idp.server.control_plane.management.permission.io.PermissionManagementResponse;
import org.idp.server.control_plane.management.permission.io.PermissionManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.permission.Permission;
import org.idp.server.core.openid.identity.permission.PermissionCommandRepository;
import org.idp.server.core.openid.identity.permission.PermissionIdentifier;
import org.idp.server.core.openid.identity.permission.PermissionQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for deleting permissions.
 *
 * <p>Handles permission deletion logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Existing permission retrieval
 *   <li>Permission deletion (or dry-run simulation)
 * </ul>
 */
public class PermissionDeleteService
    implements PermissionManagementService<PermissionDeleteRequest> {

  private final PermissionQueryRepository permissionQueryRepository;
  private final PermissionCommandRepository permissionCommandRepository;

  public PermissionDeleteService(
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
      PermissionDeleteRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    PermissionIdentifier identifier = request.identifier();
    Permission permission = permissionQueryRepository.find(tenant, identifier);

    if (!permission.exists()) {
      throw new ResourceNotFoundException(
          String.format("Permission not found: %s", identifier.value()));
    }

    // Populate builder with permission to be deleted
    builder.withBefore(permission);

    if (dryRun) {
      Map<String, Object> response = new java.util.HashMap<>();
      response.put("message", "Deletion simulated successfully");
      response.put("id", permission.id());
      response.put("dry_run", true);
      return new PermissionManagementResponse(PermissionManagementStatus.OK, response);
    }

    // Repository operation
    permissionCommandRepository.delete(tenant, permission);

    return new PermissionManagementResponse(PermissionManagementStatus.NO_CONTENT, Map.of());
  }
}

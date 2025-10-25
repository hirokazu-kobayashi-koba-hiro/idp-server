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

import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.permission.PermissionManagementContextBuilder;
import org.idp.server.control_plane.management.permission.io.PermissionFindRequest;
import org.idp.server.control_plane.management.permission.io.PermissionManagementResponse;
import org.idp.server.control_plane.management.permission.io.PermissionManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.permission.Permission;
import org.idp.server.core.openid.identity.permission.PermissionIdentifier;
import org.idp.server.core.openid.identity.permission.PermissionQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for retrieving a single permission by identifier.
 *
 * <p>Handles permission retrieval logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Permission retrieval by identifier
 *   <li>NOT_FOUND response when permission doesn't exist
 * </ul>
 */
public class PermissionFindService implements PermissionManagementService<PermissionFindRequest> {

  private final PermissionQueryRepository permissionQueryRepository;

  public PermissionFindService(PermissionQueryRepository permissionQueryRepository) {
    this.permissionQueryRepository = permissionQueryRepository;
  }

  @Override
  public PermissionManagementResponse execute(
      PermissionManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      PermissionFindRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    PermissionIdentifier identifier = request.identifier();
    Permission permission = permissionQueryRepository.find(tenant, identifier);

    if (!permission.exists()) {
      throw new ResourceNotFoundException(
          String.format("Permission not found: %s", identifier.value()));
    }

    // Populate builder with found permission
    builder.withBefore(permission);

    return new PermissionManagementResponse(PermissionManagementStatus.OK, permission.toMap());
  }
}

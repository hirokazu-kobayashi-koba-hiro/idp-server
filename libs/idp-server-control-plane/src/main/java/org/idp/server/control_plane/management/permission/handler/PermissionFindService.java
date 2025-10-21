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
 * Service for finding a single permission.
 *
 * <p>Handles permission retrieval by identifier.
 */
public class PermissionFindService implements PermissionManagementService<PermissionIdentifier> {

  private final PermissionQueryRepository permissionQueryRepository;

  public PermissionFindService(PermissionQueryRepository permissionQueryRepository) {
    this.permissionQueryRepository = permissionQueryRepository;
  }

  @Override
  public PermissionManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      PermissionIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Permission permission = permissionQueryRepository.find(tenant, identifier);

    if (!permission.exists()) {
      throw new ResourceNotFoundException(
          String.format("Permission not found: %s", identifier.value()));
    }

    return PermissionManagementResult.success(
        tenant,
        new PermissionManagementResponse(PermissionManagementStatus.OK, permission.toMap()));
  }
}

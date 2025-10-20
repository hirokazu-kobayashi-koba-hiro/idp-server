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
import org.idp.server.control_plane.management.permission.PermissionUpdateContext;
import org.idp.server.control_plane.management.permission.PermissionUpdateContextCreator;
import org.idp.server.control_plane.management.permission.validator.PermissionUpdateRequestValidator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.permission.Permission;
import org.idp.server.core.openid.identity.permission.PermissionCommandRepository;
import org.idp.server.core.openid.identity.permission.PermissionQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for updating permissions.
 *
 * <p>Handles permission update logic following the Handler/Service pattern.
 */
public class PermissionUpdateService
    implements PermissionManagementService<PermissionUpdateRequest> {

  private final PermissionQueryRepository permissionQueryRepository;
  private final PermissionCommandRepository permissionCommandRepository;

  public PermissionUpdateService(
      PermissionQueryRepository permissionQueryRepository,
      PermissionCommandRepository permissionCommandRepository) {
    this.permissionQueryRepository = permissionQueryRepository;
    this.permissionCommandRepository = permissionCommandRepository;
  }

  @Override
  public PermissionManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      PermissionUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    new PermissionUpdateRequestValidator(request.permissionRequest(), dryRun).validate();

    Permission before = permissionQueryRepository.find(tenant, request.identifier());
    if (!before.exists()) {
      throw new ResourceNotFoundException(
          String.format("Permission not found: %s", request.identifier().value()));
    }

    PermissionUpdateContextCreator creator =
        new PermissionUpdateContextCreator(tenant, before, request.permissionRequest(), dryRun);
    PermissionUpdateContext context = creator.create();

    if (!dryRun) {
      permissionCommandRepository.update(tenant, context.after());
    }

    return PermissionManagementResult.success(tenant, context.toResponse(), context);
  }
}

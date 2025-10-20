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

import org.idp.server.control_plane.management.permission.PermissionRegistrationContext;
import org.idp.server.control_plane.management.permission.PermissionRegistrationContextCreator;
import org.idp.server.control_plane.management.permission.io.PermissionRequest;
import org.idp.server.control_plane.management.permission.validator.PermissionRequestValidator;
import org.idp.server.control_plane.management.permission.verifier.PermissionRegistrationVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.permission.PermissionCommandRepository;
import org.idp.server.core.openid.identity.permission.PermissionQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
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
 *   <li>Context creation via PermissionRegistrationContextCreator
 *   <li>Business rule verification via PermissionRegistrationVerifier (throws
 *       InvalidRequestException)
 *   <li>Permission registration (or dry-run simulation)
 * </ul>
 */
public class PermissionCreateService implements PermissionManagementService<PermissionRequest> {

  private final PermissionQueryRepository permissionQueryRepository;
  private final PermissionCommandRepository permissionCommandRepository;
  private final PermissionRegistrationVerifier verifier;

  public PermissionCreateService(
      PermissionQueryRepository permissionQueryRepository,
      PermissionCommandRepository permissionCommandRepository) {
    this.permissionQueryRepository = permissionQueryRepository;
    this.permissionCommandRepository = permissionCommandRepository;
    this.verifier =
        new PermissionRegistrationVerifier(
            new org.idp.server.control_plane.management.permission.verifier.PermissionVerifier(
                permissionQueryRepository));
  }

  @Override
  public PermissionManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      PermissionRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    new PermissionRequestValidator(request, dryRun).validate();

    PermissionRegistrationContextCreator creator =
        new PermissionRegistrationContextCreator(tenant, request, dryRun);
    PermissionRegistrationContext context = creator.create();

    verifier.verify(context);

    if (!dryRun) {
      permissionCommandRepository.register(tenant, context.permission());
    }

    return PermissionManagementResult.success(tenant, context.toResponse(), context);
  }
}

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

package org.idp.server.control_plane.management.tenant.handler;

import org.idp.server.control_plane.management.tenant.TenantManagementUpdateContext;
import org.idp.server.control_plane.management.tenant.TenantManagementUpdateContextCreator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for updating tenants.
 *
 * <p>Handles tenant update logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Tenant existence verification
 *   <li>Context creation (before/after comparison)
 *   <li>Tenant update in repository
 * </ul>
 */
public class TenantUpdateService implements TenantManagementService<TenantUpdateRequest> {

  private final TenantQueryRepository tenantQueryRepository;
  private final TenantCommandRepository tenantCommandRepository;

  public TenantUpdateService(
      TenantQueryRepository tenantQueryRepository,
      TenantCommandRepository tenantCommandRepository) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.tenantCommandRepository = tenantCommandRepository;
  }

  @Override
  public TenantManagementResult execute(
      Tenant adminTenant,
      User operator,
      OAuthToken oAuthToken,
      TenantUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Retrieve existing tenant (throws ResourceNotFoundException if not found)
    Tenant before = tenantQueryRepository.get(request.tenantIdentifier());

    // 2. Context creation
    TenantManagementUpdateContextCreator contextCreator =
        new TenantManagementUpdateContextCreator(
            adminTenant, before, request.tenantRequest(), operator, dryRun);
    TenantManagementUpdateContext context = contextCreator.create();

    // 4. Dry-run check
    if (dryRun) {
      return TenantManagementResult.success(adminTenant, context, context.toResponse());
    }

    // 5. Repository operation
    tenantCommandRepository.update(context.after());

    return TenantManagementResult.success(adminTenant, context, context.toResponse());
  }
}

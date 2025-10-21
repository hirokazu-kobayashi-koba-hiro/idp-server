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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for deleting tenants.
 *
 * <p>Handles tenant deletion logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Tenant existence verification
 *   <li>Tenant deletion from repository
 * </ul>
 */
public class TenantDeletionService implements TenantManagementService<TenantIdentifier> {

  private final TenantQueryRepository tenantQueryRepository;
  private final TenantCommandRepository tenantCommandRepository;

  public TenantDeletionService(
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
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Retrieve existing tenant (throws ResourceNotFoundException if not found)
    Tenant before = tenantQueryRepository.get(tenantIdentifier);

    // 2. Dry-run check
    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Deletion simulated successfully");
      response.put("id", tenantIdentifier.value());
      response.put("dry_run", true);
      TenantManagementResponse dryRunResponse =
          new TenantManagementResponse(TenantManagementStatus.OK, response);
      return TenantManagementResult.success(adminTenant, null, dryRunResponse);
    }

    // 4. Repository operation
    tenantCommandRepository.delete(tenantIdentifier);

    // 5. Return NO_CONTENT response
    TenantManagementResponse response =
        new TenantManagementResponse(TenantManagementStatus.NO_CONTENT, Map.of());
    return TenantManagementResult.success(adminTenant, null, response);
  }
}

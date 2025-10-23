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

import org.idp.server.control_plane.management.tenant.TenantManagementContextBuilder;
import org.idp.server.control_plane.management.tenant.io.TenantFindRequest;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for finding a single tenant.
 *
 * <p>Handles tenant retrieval logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Tenant retrieval from repository
 *   <li>Existence verification
 * </ul>
 *
 * <h2>NOT Responsibilities (handled by Handler/EntryService)</h2>
 *
 * <ul>
 *   <li>Permission checking
 *   <li>Admin tenant retrieval
 *   <li>Audit logging
 *   <li>Transaction management
 * </ul>
 */
public class TenantFindService implements TenantManagementService<TenantFindRequest> {

  private final TenantQueryRepository tenantQueryRepository;

  public TenantFindService(TenantQueryRepository tenantQueryRepository) {
    this.tenantQueryRepository = tenantQueryRepository;
  }

  @Override
  public TenantManagementResponse execute(
      TenantManagementContextBuilder builder,
      Tenant adminTenant,
      User operator,
      OAuthToken oAuthToken,
      TenantFindRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    TenantIdentifier tenantIdentifier = request.tenantIdentifier();
    // 1. Retrieve tenant (throws ResourceNotFoundException if not found)
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // 2. Return success result (no context for read-only operation)
    return new TenantManagementResponse(TenantManagementStatus.OK, tenant.toMap());
  }
}

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
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.tenant.TenantManagementContextBuilder;
import org.idp.server.control_plane.management.tenant.io.TenantFindListRequest;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for finding list of tenants within an organization.
 *
 * <p>Organization-specific implementation that retrieves tenants assigned to the organization.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Organization assigned tenants retrieval
 *   <li>Tenant list query based on organization's tenant assignments
 * </ul>
 */
public class OrgTenantFindListService implements TenantManagementService<TenantFindListRequest> {

  private final TenantQueryRepository tenantQueryRepository;

  public OrgTenantFindListService(TenantQueryRepository tenantQueryRepository) {
    this.tenantQueryRepository = tenantQueryRepository;
  }

  @Override
  public TenantManagementResponse execute(
      TenantManagementContextBuilder builder,
      Tenant orgTenant,
      User operator,
      OAuthToken oAuthToken,
      TenantFindListRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Get organization's assigned tenant identifiers
    List<TenantIdentifier> organizationTenantIds = request.tenantIdentifiers();

    // 2. Retrieve tenant list
    List<Tenant> tenants = tenantQueryRepository.findList(organizationTenantIds);

    // 3. Build response
    Map<String, Object> responseMap = new HashMap<>();
    responseMap.put("list", tenants.stream().map(Tenant::toMap).toList());

    return new TenantManagementResponse(TenantManagementStatus.OK, responseMap);
  }
}

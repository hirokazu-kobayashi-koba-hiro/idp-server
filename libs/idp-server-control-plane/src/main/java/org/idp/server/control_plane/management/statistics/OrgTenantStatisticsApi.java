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

package org.idp.server.control_plane.management.statistics;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.TenantStatisticsQueries;
import org.idp.server.platform.statistics.TenantStatisticsReportQuery;
import org.idp.server.platform.type.RequestAttributes;

public interface OrgTenantStatisticsApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put("findByDateRange", new AdminPermissions(Set.of(DefaultAdminPermission.TENANT_READ)));
    map.put("findYearlyReport", new AdminPermissions(Set.of(DefaultAdminPermission.TENANT_READ)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  TenantStatisticsResponse findByDateRange(
      OrganizationAuthenticationContext authenticationContext,
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      TenantStatisticsQueries queries,
      RequestAttributes requestAttributes);

  TenantStatisticsResponse findYearlyReport(
      OrganizationAuthenticationContext authenticationContext,
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      TenantStatisticsReportQuery query,
      RequestAttributes requestAttributes);
}

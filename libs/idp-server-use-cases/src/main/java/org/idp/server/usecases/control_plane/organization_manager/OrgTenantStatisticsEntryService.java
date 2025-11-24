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

package org.idp.server.usecases.control_plane.organization_manager;

import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.management.statistics.OrgTenantStatisticsApi;
import org.idp.server.control_plane.management.statistics.TenantStatisticsFindService;
import org.idp.server.control_plane.management.statistics.TenantStatisticsResponse;
import org.idp.server.control_plane.management.statistics.handler.OrgTenantStatisticsManagementHandler;
import org.idp.server.control_plane.management.statistics.handler.TenantStatisticsManagementResult;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.statistics.TenantStatisticsQueries;
import org.idp.server.platform.statistics.repository.TenantStatisticsDataQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level tenant statistics entry service.
 *
 * <p>This service implements organization-scoped tenant statistics operations that allow
 * organization administrators to view statistics for tenants within their organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization membership verification</strong> - Ensures the operator belongs to the
 *       organization
 *   <li><strong>Tenant access verification</strong> - Validates the tenant belongs to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary TENANT_READ
 *       permission
 * </ol>
 *
 * <p>All operations provide comprehensive audit logging for organization-level statistics access.
 *
 * @see OrgTenantStatisticsApi
 * @see OrganizationAccessVerifier
 */
@Transaction
public class OrgTenantStatisticsEntryService implements OrgTenantStatisticsApi {

  private final OrgTenantStatisticsManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public OrgTenantStatisticsEntryService(
      TenantStatisticsDataQueryRepository repository,
      OrganizationRepository organizationRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    TenantStatisticsFindService findService = new TenantStatisticsFindService(repository);
    this.handler =
        new OrgTenantStatisticsManagementHandler(
            findService, this, organizationRepository, tenantQueryRepository);
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public TenantStatisticsResponse findByDateRange(
      OrganizationAuthenticationContext authenticationContext,
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      TenantStatisticsQueries queries,
      RequestAttributes requestAttributes) {

    TenantStatisticsManagementResult result =
        handler.handle(
            authenticationContext,
            organizationIdentifier,
            tenantIdentifier,
            queries,
            requestAttributes);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }
}

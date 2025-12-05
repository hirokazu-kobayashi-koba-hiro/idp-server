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

package org.idp.server.usecases.control_plane.system_manager;

import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.management.statistics.TenantStatisticsApi;
import org.idp.server.control_plane.management.statistics.TenantStatisticsFindService;
import org.idp.server.control_plane.management.statistics.TenantStatisticsReportFindService;
import org.idp.server.control_plane.management.statistics.TenantStatisticsResponse;
import org.idp.server.control_plane.management.statistics.handler.TenantStatisticsManagementHandler;
import org.idp.server.control_plane.management.statistics.handler.TenantStatisticsManagementResult;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.statistics.TenantStatisticsQueries;
import org.idp.server.platform.statistics.TenantStatisticsReportQuery;
import org.idp.server.platform.statistics.repository.TenantStatisticsQueryRepository;
import org.idp.server.platform.statistics.repository.TenantYearlyStatisticsQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class TenantStatisticsEntryService implements TenantStatisticsApi {

  private final TenantStatisticsManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public TenantStatisticsEntryService(
      TenantStatisticsQueryRepository repository,
      TenantYearlyStatisticsQueryRepository yearlyRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    TenantStatisticsFindService findService = new TenantStatisticsFindService(repository);
    TenantStatisticsReportFindService reportFindService =
        new TenantStatisticsReportFindService(repository, yearlyRepository);
    this.handler =
        new TenantStatisticsManagementHandler(
            findService, reportFindService, this, tenantQueryRepository);
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public TenantStatisticsResponse findByDateRange(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      TenantStatisticsQueries queries,
      RequestAttributes requestAttributes) {

    TenantStatisticsManagementResult result =
        handler.handle(authenticationContext, tenantIdentifier, queries, requestAttributes);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }

  @Override
  public TenantStatisticsResponse findYearlyReport(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      TenantStatisticsReportQuery query,
      RequestAttributes requestAttributes) {

    TenantStatisticsManagementResult result =
        handler.handleReport(authenticationContext, tenantIdentifier, query, requestAttributes);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }
}

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

package org.idp.server.control_plane.management.statistics.handler;

import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.ApiPermissionVerifier;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.statistics.TenantStatisticsApi;
import org.idp.server.control_plane.management.statistics.TenantStatisticsContext;
import org.idp.server.control_plane.management.statistics.TenantStatisticsResponse;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.statistics.TenantStatisticsQueries;
import org.idp.server.platform.statistics.TenantStatisticsReportQuery;
import org.idp.server.platform.type.RequestAttributes;

/**
 * System-level tenant statistics management handler.
 *
 * <p>Orchestrates tenant statistics operations by delegating to appropriate Service
 * implementations.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Tenant retrieval and validation
 *   <li>Permission verification
 *   <li>Service execution
 *   <li>Exception catching and Result wrapping
 * </ul>
 */
public class TenantStatisticsManagementHandler {

  private final TenantStatisticsManagementService<TenantStatisticsQueries> findService;
  private final TenantStatisticsManagementService<TenantStatisticsReportQuery> reportFindService;
  private final TenantStatisticsApi managementApi;
  private final TenantQueryRepository tenantQueryRepository;
  private final ApiPermissionVerifier apiPermissionVerifier;
  LoggerWrapper log = LoggerWrapper.getLogger(TenantStatisticsManagementHandler.class);

  public TenantStatisticsManagementHandler(
      TenantStatisticsManagementService<TenantStatisticsQueries> findService,
      TenantStatisticsManagementService<TenantStatisticsReportQuery> reportFindService,
      TenantStatisticsApi managementApi,
      TenantQueryRepository tenantQueryRepository) {
    this.findService = findService;
    this.reportFindService = reportFindService;
    this.managementApi = managementApi;
    this.tenantQueryRepository = tenantQueryRepository;
    this.apiPermissionVerifier = new ApiPermissionVerifier();
  }

  public TenantStatisticsManagementResult handle(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      TenantStatisticsQueries queries,
      RequestAttributes requestAttributes) {

    User operator = authenticationContext.operator();
    OAuthToken oAuthToken = authenticationContext.oAuthToken();

    try {

      Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

      // Permission verification
      AdminPermissions requiredPermissions =
          managementApi.getRequiredPermissions("findByDateRange");
      apiPermissionVerifier.verify(operator, requiredPermissions);

      // Delegate to service
      TenantStatisticsResponse response =
          findService.execute(targetTenant, operator, oAuthToken, queries, requestAttributes);

      TenantStatisticsContext context =
          new TenantStatisticsContext(
              tenantIdentifier, queries, operator, oAuthToken, requestAttributes);
      return TenantStatisticsManagementResult.success(context, response);
    } catch (NotFoundException e) {

      log.warn(e.getMessage());
      ResourceNotFoundException notFound = new ResourceNotFoundException(e.getMessage());
      TenantStatisticsContext context =
          new TenantStatisticsContext(
              tenantIdentifier, queries, operator, oAuthToken, requestAttributes);
      return TenantStatisticsManagementResult.error(context, notFound);
    } catch (ManagementApiException e) {

      log.warn(e.getMessage());
      TenantStatisticsContext context =
          new TenantStatisticsContext(
              tenantIdentifier, queries, operator, oAuthToken, requestAttributes);
      return TenantStatisticsManagementResult.error(context, e);
    }
  }

  public TenantStatisticsManagementResult handleReport(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      TenantStatisticsReportQuery query,
      RequestAttributes requestAttributes) {

    User operator = authenticationContext.operator();
    OAuthToken oAuthToken = authenticationContext.oAuthToken();

    try {

      Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

      AdminPermissions requiredPermissions =
          managementApi.getRequiredPermissions("findYearlyReport");
      apiPermissionVerifier.verify(operator, requiredPermissions);

      TenantStatisticsResponse response =
          reportFindService.execute(targetTenant, operator, oAuthToken, query, requestAttributes);

      TenantStatisticsContext context =
          new TenantStatisticsContext(
              tenantIdentifier, null, operator, oAuthToken, requestAttributes);
      return TenantStatisticsManagementResult.success(context, response);
    } catch (NotFoundException e) {

      log.warn(e.getMessage());
      ResourceNotFoundException notFound = new ResourceNotFoundException(e.getMessage());
      TenantStatisticsContext context =
          new TenantStatisticsContext(
              tenantIdentifier, null, operator, oAuthToken, requestAttributes);
      return TenantStatisticsManagementResult.error(context, notFound);
    } catch (ManagementApiException e) {

      log.warn(e.getMessage());
      TenantStatisticsContext context =
          new TenantStatisticsContext(
              tenantIdentifier, null, operator, oAuthToken, requestAttributes);
      return TenantStatisticsManagementResult.error(context, e);
    }
  }
}

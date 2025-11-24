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

import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.statistics.OrgTenantStatisticsApi;
import org.idp.server.control_plane.management.statistics.OrgTenantStatisticsContext;
import org.idp.server.control_plane.management.statistics.TenantStatisticsResponse;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.statistics.TenantStatisticsQueries;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level tenant statistics management handler.
 *
 * <p>Orchestrates tenant statistics operations within an organization context by delegating to
 * appropriate Service implementations.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Organization retrieval and validation
 *   <li>Tenant retrieval and validation
 *   <li>Organization access verification (4-step verification)
 *   <li>Service execution
 *   <li>Exception catching and Result wrapping
 * </ul>
 */
public class OrgTenantStatisticsManagementHandler {

  private final TenantStatisticsManagementService<TenantStatisticsQueries> findService;
  private final OrgTenantStatisticsApi managementApi;
  private final OrganizationRepository organizationRepository;
  private final TenantQueryRepository tenantQueryRepository;
  private final OrganizationAccessVerifier organizationAccessVerifier;
  LoggerWrapper log = LoggerWrapper.getLogger(OrgTenantStatisticsManagementHandler.class);

  public OrgTenantStatisticsManagementHandler(
      TenantStatisticsManagementService<TenantStatisticsQueries> findService,
      OrgTenantStatisticsApi managementApi,
      OrganizationRepository organizationRepository,
      TenantQueryRepository tenantQueryRepository) {
    this.findService = findService;
    this.managementApi = managementApi;
    this.organizationRepository = organizationRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
  }

  public TenantStatisticsManagementResult handle(
      OrganizationAuthenticationContext authenticationContext,
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      TenantStatisticsQueries queries,
      RequestAttributes requestAttributes) {

    User operator = authenticationContext.operator();
    OAuthToken oAuthToken = authenticationContext.oAuthToken();

    try {

      // Step 1: Verify organization access (includes 4-step verification)
      Organization organization = organizationRepository.get(organizationIdentifier);
      organizationAccessVerifier.verify(
          organization,
          tenantIdentifier,
          operator,
          managementApi.getRequiredPermissions("findByDateRange"));

      // Step 2: Get target tenant
      Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

      // Step 3: Delegate to service
      TenantStatisticsResponse response =
          findService.execute(targetTenant, operator, oAuthToken, queries, requestAttributes);

      OrgTenantStatisticsContext context =
          new OrgTenantStatisticsContext(
              organizationIdentifier,
              tenantIdentifier,
              queries,
              operator,
              oAuthToken,
              requestAttributes);
      return TenantStatisticsManagementResult.success(context, response);
    } catch (NotFoundException e) {

      log.warn(e.getMessage());
      ResourceNotFoundException notFound = new ResourceNotFoundException(e.getMessage());
      OrgTenantStatisticsContext context =
          new OrgTenantStatisticsContext(
              organizationIdentifier,
              tenantIdentifier,
              queries,
              operator,
              oAuthToken,
              requestAttributes);
      return TenantStatisticsManagementResult.error(context, notFound);
    } catch (ManagementApiException e) {

      log.warn(e.getMessage());
      OrgTenantStatisticsContext context =
          new OrgTenantStatisticsContext(
              organizationIdentifier,
              tenantIdentifier,
              queries,
              operator,
              oAuthToken,
              requestAttributes);
      return TenantStatisticsManagementResult.error(context, e);
    }
  }
}

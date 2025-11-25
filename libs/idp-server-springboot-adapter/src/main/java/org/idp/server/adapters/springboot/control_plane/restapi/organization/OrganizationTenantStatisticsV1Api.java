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

package org.idp.server.adapters.springboot.control_plane.restapi.organization;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.control_plane.model.OrganizationOperatorPrincipal;
import org.idp.server.control_plane.management.statistics.OrgTenantStatisticsApi;
import org.idp.server.control_plane.management.statistics.TenantStatisticsResponse;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.TenantStatisticsQueries;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Organization-level tenant statistics API controller.
 *
 * <p>This controller handles tenant statistics operations within an organization context. It
 * provides read-only operations for statistics belonging to a specific organization and tenant,
 * with proper authentication and authorization through the OrganizationOperatorPrincipal.
 *
 * <p>All operations are performed within the context of the organization's admin tenant, ensuring
 * proper isolation and access control.
 *
 * <p>API endpoints: - GET
 * /organizations/{organizationId}/tenants/{tenantId}/statistics?from=YYYY-MM-DD&to=YYYY-MM-DD - Get
 * tenant statistics
 *
 * @see OrgTenantStatisticsApi
 * @see OrganizationOperatorPrincipal
 */
@RestController
@RequestMapping("/v1/management/organizations/{organizationId}/tenants/{tenant-id}/statistics")
public class OrganizationTenantStatisticsV1Api implements ParameterTransformable {

  OrgTenantStatisticsApi orgTenantStatisticsApi;

  public OrganizationTenantStatisticsV1Api(IdpServerApplication idpServerApplication) {
    this.orgTenantStatisticsApi = idpServerApplication.orgTenantStatisticsApi();
  }

  /**
   * Retrieves tenant statistics within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantIdentifier the tenant identifier from path
   * @param queryParams query parameters (e.g., from, to)
   * @param httpServletRequest the HTTP request
   * @return the statistics response
   */
  @GetMapping
  public ResponseEntity<?> getStatistics(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    TenantStatisticsQueries queries = new TenantStatisticsQueries(queryParams);

    TenantStatisticsResponse response =
        orgTenantStatisticsApi.findByDateRange(
            organizationOperatorPrincipal.authenticationContext(),
            new OrganizationIdentifier(organizationId),
            tenantIdentifier,
            queries,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");

    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}

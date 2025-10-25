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
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.control_plane.model.OrganizationOperatorPrincipal;
import org.idp.server.control_plane.management.security.event.OrgSecurityEventManagementApi;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.SecurityEventQueries;
import org.idp.server.platform.security.event.SecurityEventIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Organization-level security event management API controller.
 *
 * <p>This controller handles security event management operations within an organization context.
 * It provides read-only operations for security events belonging to a specific organization and
 * tenant, with proper authentication and authorization through the OrganizationOperatorPrincipal.
 *
 * <p>All operations are performed within the context of the organization's admin tenant, ensuring
 * proper isolation and access control.
 *
 * <p>API endpoints: - GET /organizations/{organizationId}/tenants/{tenantId}/security-events - List
 * organization security events - GET
 * /organizations/{organizationId}/tenants/{tenantId}/security-events/{eventId} - Get specific
 * security event
 *
 * @see OrgSecurityEventManagementApi
 * @see OrganizationOperatorPrincipal
 */
@RestController
@RequestMapping("/v1/management/organizations/{organizationId}/tenants/{tenantId}/security-events")
public class OrganizationSecurityEventManagementV1Api implements ParameterTransformable {

  OrgSecurityEventManagementApi orgSecurityEventManagementApi;

  public OrganizationSecurityEventManagementV1Api(IdpServerApplication idpServerApplication) {
    this.orgSecurityEventManagementApi = idpServerApplication.orgSecurityEventManagementApi();
  }

  /**
   * Lists all security events belonging to the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param queryParams all query parameters including limit, offset, event_type, from, to,
   *     client_id, user_id, external_user_id, details.*
   * @param httpServletRequest the HTTP request
   * @return the security event list response
   */
  @GetMapping
  public ResponseEntity<?> getList(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    SecurityEventQueries queries = new SecurityEventQueries(queryParams);

    SecurityEventManagementResponse response =
        orgSecurityEventManagementApi.findList(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            queries,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Gets a specific security event within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param eventId the security event identifier from path
   * @param httpServletRequest the HTTP request
   * @return the security event details response
   */
  @GetMapping("/{eventId}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable String eventId,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    SecurityEventManagementResponse response =
        orgSecurityEventManagementApi.get(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            new SecurityEventIdentifier(eventId),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}

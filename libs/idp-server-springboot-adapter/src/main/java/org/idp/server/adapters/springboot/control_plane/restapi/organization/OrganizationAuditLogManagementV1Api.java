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
import org.idp.server.control_plane.management.audit.OrgAuditLogManagementApi;
import org.idp.server.control_plane.management.audit.io.AuditLogManagementResponse;
import org.idp.server.platform.audit.AuditLogIdentifier;
import org.idp.server.platform.audit.AuditLogQueries;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Organization-level audit log management API controller.
 *
 * <p>This controller handles audit log management operations within an organization context. It
 * provides read-only operations for audit logs belonging to a specific organization and tenant,
 * with proper authentication and authorization through the OrganizationOperatorPrincipal.
 *
 * <p>All operations are performed within the context of the organization's admin tenant, ensuring
 * proper isolation and access control.
 *
 * <p>API endpoints: - GET /organizations/{organizationId}/tenants/{tenantId}/audit-logs - List
 * organization audit logs - GET
 * /organizations/{organizationId}/tenants/{tenantId}/audit-logs/{logId} - Get specific audit log
 *
 * @see OrgAuditLogManagementApi
 * @see OrganizationOperatorPrincipal
 */
@RestController
@RequestMapping("/v1/management/organizations/{organizationId}/tenants/{tenantId}/audit-logs")
public class OrganizationAuditLogManagementV1Api implements ParameterTransformable {

  OrgAuditLogManagementApi orgAuditLogManagementApi;

  public OrganizationAuditLogManagementV1Api(IdpServerApplication idpServerApplication) {
    this.orgAuditLogManagementApi = idpServerApplication.orgAuditLogManagementApi();
  }

  /**
   * Lists all audit logs belonging to the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param queryParams all query parameters including limit, offset, from, to, type, description,
   *     target_resource, target_action, client_id, user_id, external_user_id, attributes.*
   * @param httpServletRequest the HTTP request
   * @return the audit log list response
   */
  @GetMapping
  public ResponseEntity<?> getList(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    OrganizationIdentifier organizationIdentifier =
        organizationOperatorPrincipal.getOrganizationId();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuditLogQueries queries = new AuditLogQueries(queryParams);

    AuditLogManagementResponse response =
        orgAuditLogManagementApi.findList(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            queries,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Gets a specific audit log within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param logId the audit log identifier from path
   * @param httpServletRequest the HTTP request
   * @return the audit log details response
   */
  @GetMapping("/{logId}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable String logId,
      HttpServletRequest httpServletRequest) {

    OrganizationIdentifier organizationIdentifier =
        organizationOperatorPrincipal.getOrganizationId();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuditLogManagementResponse response =
        orgAuditLogManagementApi.get(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            new AuditLogIdentifier(logId),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}

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
import org.idp.server.control_plane.management.oidc.authorization.OrgAuthorizationServerManagementApi;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementResponse;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerUpdateRequest;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Organization-level authorization server management REST API controller.
 *
 * <p>This controller provides organization-scoped authorization server configuration management
 * endpoints for organization administrators. It handles HTTP requests and delegates to the
 * organization authorization server management entry service.
 *
 * <p>Supported operations:
 *
 * <ul>
 *   <li>GET - Retrieve authorization server configuration for a tenant within an organization
 *   <li>PUT - Update authorization server configuration for a tenant within an organization (with
 *       dry-run support)
 * </ul>
 *
 * @see OrgAuthorizationServerManagementApi
 */
@RestController
@RequestMapping(
    "/v1/management/organizations/{organizationId}/tenants/{tenantId}/authorization-server")
public class OrganizationAuthorizationServerManagementV1Api implements ParameterTransformable {

  OrgAuthorizationServerManagementApi orgAuthorizationServerManagementApi;

  public OrganizationAuthorizationServerManagementV1Api(IdpServerApplication idpServerApplication) {
    this.orgAuthorizationServerManagementApi =
        idpServerApplication.orgAuthorizationServerManagementApi();
  }

  /**
   * Retrieves authorization server configuration for a tenant within an organization.
   *
   * @param organizationOperatorPrincipal the authenticated operator
   * @param organizationId the organization identifier
   * @param tenantId the tenant identifier
   * @param httpServletRequest the HTTP request
   * @return authorization server configuration details
   */
  @GetMapping
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable("organizationId") OrganizationIdentifier organizationId,
      @PathVariable("tenantId") TenantIdentifier tenantId,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthorizationServerManagementResponse response =
        orgAuthorizationServerManagementApi.get(
            organizationId,
            tenantId,
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");

    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Updates authorization server configuration for a tenant within an organization.
   *
   * @param operatorPrincipal the authenticated operator
   * @param organizationId the organization identifier
   * @param tenantId the tenant identifier
   * @param body the update request body
   * @param dryRun whether to perform a dry run (preview only)
   * @param httpServletRequest the HTTP request
   * @return updated authorization server configuration or preview
   */
  @PutMapping
  public ResponseEntity<?> put(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable("organizationId") OrganizationIdentifier organizationId,
      @PathVariable("tenantId") TenantIdentifier tenantId,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthorizationServerManagementResponse response =
        orgAuthorizationServerManagementApi.update(
            organizationId,
            tenantId,
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            new AuthorizationServerUpdateRequest(body),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");

    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}

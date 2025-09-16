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
import org.idp.server.control_plane.management.authentication.transaction.OrgAuthenticationTransactionManagementApi;
import org.idp.server.control_plane.management.authentication.transaction.io.AuthenticationTransactionManagementResponse;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.AuthenticationTransactionQueries;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Organization-level authentication transaction management API controller.
 *
 * <p>This controller handles authentication transaction monitoring operations within an
 * organization context. It provides read-only operations for authentication transactions belonging
 * to a specific organization and tenant, with proper authentication and authorization through the
 * OrganizationOperatorPrincipal.
 *
 * <p>All operations are performed within the context of the organization's admin tenant, ensuring
 * proper isolation and access control.
 *
 * <p>API endpoints: - GET
 * /organizations/{organizationId}/tenants/{tenantId}/authentication-transactions - List
 * authentication transactions - GET
 * /organizations/{organizationId}/tenants/{tenantId}/authentication-transactions/{transactionId} -
 * Get specific transaction
 *
 * @see OrgAuthenticationTransactionManagementApi
 * @see OrganizationOperatorPrincipal
 */
@RestController
@RequestMapping(
    "/v1/management/organizations/{organizationId}/tenants/{tenantId}/authentication-transactions")
public class OrganizationAuthenticationTransactionManagementV1Api
    implements ParameterTransformable {

  OrgAuthenticationTransactionManagementApi orgAuthenticationTransactionManagementApi;

  public OrganizationAuthenticationTransactionManagementV1Api(
      IdpServerApplication idpServerApplication) {
    this.orgAuthenticationTransactionManagementApi =
        idpServerApplication.orgAuthenticationTransactionManagementApi();
  }

  /**
   * Lists authentication transactions within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param queryParams the query parameters for filtering
   * @param httpServletRequest the HTTP request
   * @return the authentication transaction list response
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

    AuthenticationTransactionManagementResponse response =
        orgAuthenticationTransactionManagementApi.findList(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            new AuthenticationTransactionQueries(queryParams),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Gets a specific authentication transaction within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param transactionId the authentication transaction identifier
   * @param httpServletRequest the HTTP request
   * @return the authentication transaction details response
   */
  @GetMapping("/{transactionId}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable("transactionId") AuthenticationTransactionIdentifier transactionId,
      HttpServletRequest httpServletRequest) {

    OrganizationIdentifier organizationIdentifier =
        organizationOperatorPrincipal.getOrganizationId();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthenticationTransactionManagementResponse response =
        orgAuthenticationTransactionManagementApi.get(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            transactionId,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}

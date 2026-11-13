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
import org.idp.server.account_linking.LinkedExternalAccountIdentifier;
import org.idp.server.account_linking.LinkedExternalAccountQueries;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.control_plane.model.OrganizationOperatorPrincipal;
import org.idp.server.control_plane.management.identity.user.OrgUserManagementApi;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** The tenant's linked external accounts across users, at organization level. */
@RestController
@RequestMapping(
    "/v1/management/organizations/{organizationId}/tenants/{tenantId}/linked-external-accounts")
public class OrganizationLinkedExternalAccountManagementV1Api implements ParameterTransformable {

  OrgUserManagementApi orgUserManagementApi;

  public OrganizationLinkedExternalAccountManagementV1Api(
      IdpServerApplication idpServerApplication) {
    this.orgUserManagementApi = idpServerApplication.orgUserManagementApi();
  }

  @GetMapping
  public ResponseEntity<?> getList(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    UserManagementResponse response =
        orgUserManagementApi.findAllLinkedExternalAccounts(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            new LinkedExternalAccountQueries(queryParams),
            transform(httpServletRequest));
    return toResponse(response);
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable String id,
      HttpServletRequest httpServletRequest) {

    UserManagementResponse response =
        orgUserManagementApi.getLinkedExternalAccountById(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            new LinkedExternalAccountIdentifier(id),
            transform(httpServletRequest));
    return toResponse(response);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable String id,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    UserManagementResponse response =
        orgUserManagementApi.deleteLinkedExternalAccountById(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            new LinkedExternalAccountIdentifier(id),
            requestAttributes,
            dryRun);
    return toResponse(response);
  }

  private ResponseEntity<?> toResponse(UserManagementResponse response) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), headers, HttpStatus.valueOf(response.statusCode()));
  }
}

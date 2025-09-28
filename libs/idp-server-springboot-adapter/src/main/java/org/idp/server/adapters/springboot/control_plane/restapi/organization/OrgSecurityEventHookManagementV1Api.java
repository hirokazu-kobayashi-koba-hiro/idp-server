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
import org.idp.server.control_plane.management.security.hook_result.OrgSecurityEventHookManagementApi;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementResponse;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.hook.SecurityEventHookResultIdentifier;
import org.idp.server.platform.security.hook.SecurityEventHookResultQueries;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
    "/v1/management/organizations/{organizationId}/tenants/{tenant-id}/security-event-hooks")
public class OrgSecurityEventHookManagementV1Api implements ParameterTransformable {

  OrgSecurityEventHookManagementApi securityEventHookManagementApi;

  public OrgSecurityEventHookManagementV1Api(IdpServerApplication idpServerApplication) {
    this.securityEventHookManagementApi = idpServerApplication.orgSecurityEventHookManagementApi();
  }

  @GetMapping
  public ResponseEntity<?> getList(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable("organizationId") OrganizationIdentifier organizationIdentifier,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    SecurityEventHookManagementResponse response =
        securityEventHookManagementApi.findList(
            organizationIdentifier,
            tenantIdentifier,
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            new SecurityEventHookResultQueries(queryParams),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable("organizationId") OrganizationIdentifier organizationIdentifier,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") SecurityEventHookResultIdentifier identifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    SecurityEventHookManagementResponse response =
        securityEventHookManagementApi.get(
            organizationIdentifier,
            tenantIdentifier,
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            identifier,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/{id}/retry")
  public ResponseEntity<?> retry(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable("organizationId") OrganizationIdentifier organizationIdentifier,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") SecurityEventHookResultIdentifier identifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    SecurityEventHookManagementResponse response =
        securityEventHookManagementApi.retry(
            organizationIdentifier,
            tenantIdentifier,
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            identifier,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}

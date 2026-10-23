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

package org.idp.server.adapters.springboot.control_plane.restapi.management;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.account_linking.LinkedExternalAccountIdentifier;
import org.idp.server.account_linking.LinkedExternalAccountQueries;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.control_plane.model.OperatorPrincipal;
import org.idp.server.control_plane.management.identity.user.UserManagementApi;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** The tenant's linked external accounts across users, at system level. */
@RestController
@RequestMapping("/v1/management/tenants/{tenant-id}/linked-external-accounts")
public class LinkedExternalAccountManagementV1Api implements ParameterTransformable {

  UserManagementApi userManagementApi;

  public LinkedExternalAccountManagementV1Api(IdpServerApplication idpServerApplication) {
    this.userManagementApi = idpServerApplication.userManagementAPi();
  }

  @GetMapping
  public ResponseEntity<?> getList(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    UserManagementResponse response =
        userManagementApi.findAllLinkedExternalAccounts(
            operatorPrincipal.authenticationContext(),
            tenantIdentifier,
            new LinkedExternalAccountQueries(queryParams),
            transform(httpServletRequest));
    return toResponse(response);
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") String id,
      HttpServletRequest httpServletRequest) {

    UserManagementResponse response =
        userManagementApi.getLinkedExternalAccountById(
            operatorPrincipal.authenticationContext(),
            tenantIdentifier,
            new LinkedExternalAccountIdentifier(id),
            transform(httpServletRequest));
    return toResponse(response);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") String id,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    UserManagementResponse response =
        userManagementApi.deleteLinkedExternalAccountById(
            operatorPrincipal.authenticationContext(),
            tenantIdentifier,
            new LinkedExternalAccountIdentifier(id),
            requestAttributes,
            dryRun);
    return toResponse(response);
  }

  private ResponseEntity<?> toResponse(UserManagementResponse response) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), headers, HttpStatus.valueOf(response.statusCode()));
  }
}

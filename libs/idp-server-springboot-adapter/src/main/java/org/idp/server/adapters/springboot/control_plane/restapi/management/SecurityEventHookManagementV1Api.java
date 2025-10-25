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
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.control_plane.model.OperatorPrincipal;
import org.idp.server.control_plane.management.security.hook_result.SecurityEventHookManagementApi;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementResponse;
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
@RequestMapping("/v1/management/tenants/{tenant-id}/security-event-hooks")
public class SecurityEventHookManagementV1Api implements ParameterTransformable {

  SecurityEventHookManagementApi securityEventHookManagementApi;

  public SecurityEventHookManagementV1Api(IdpServerApplication idpServerApplication) {
    this.securityEventHookManagementApi = idpServerApplication.securityEventHookManagementApi();
  }

  @GetMapping
  public ResponseEntity<?> getList(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    SecurityEventHookManagementResponse response =
        securityEventHookManagementApi.findList(
            operatorPrincipal.authenticationContext(),
            tenantIdentifier,
            new SecurityEventHookResultQueries(queryParams),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") SecurityEventHookResultIdentifier identifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    SecurityEventHookManagementResponse response =
        securityEventHookManagementApi.get(
            operatorPrincipal.authenticationContext(),
            tenantIdentifier,
            identifier,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/{id}/retry")
  public ResponseEntity<?> retry(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") SecurityEventHookResultIdentifier identifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    SecurityEventHookManagementResponse response =
        securityEventHookManagementApi.retry(
            operatorPrincipal.authenticationContext(),
            tenantIdentifier,
            identifier,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}

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
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.control_plane.model.OperatorPrincipal;
import org.idp.server.control_plane.management.oidc.clientinstance.ClientInstanceManagementApi;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceManagementResponse;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceRegistrationRequest;
import org.idp.server.core.openid.clientinstance.ClientInstanceIdentifier;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/management/tenants/{tenant-id}/clients/{client-id}/instances")
public class ClientInstanceManagementV1Api implements ParameterTransformable {

  ClientInstanceManagementApi clientInstanceManagementApi;

  public ClientInstanceManagementV1Api(IdpServerApplication idpServerApplication) {
    this.clientInstanceManagementApi = idpServerApplication.clientInstanceManagementApi();
  }

  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("client-id") String clientId,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    RequestedClientId requestedClientId = new RequestedClientId(clientId);

    ClientInstanceManagementResponse response =
        clientInstanceManagementApi.create(
            operatorPrincipal.authenticationContext(),
            tenantIdentifier,
            requestedClientId,
            new ClientInstanceRegistrationRequest(requestedClientId, body),
            requestAttributes,
            dryRun);

    return toResponseEntity(response);
  }

  @GetMapping
  public ResponseEntity<?> getList(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("client-id") String clientId,
      @RequestParam(value = "limit", required = false, defaultValue = "20") int limit,
      @RequestParam(value = "offset", required = false, defaultValue = "0") int offset,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    ClientInstanceManagementResponse response =
        clientInstanceManagementApi.findList(
            operatorPrincipal.authenticationContext(),
            tenantIdentifier,
            new RequestedClientId(clientId),
            limit,
            offset,
            requestAttributes);

    return toResponseEntity(response);
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("client-id") String clientId,
      @PathVariable("id") String id,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    ClientInstanceManagementResponse response =
        clientInstanceManagementApi.get(
            operatorPrincipal.authenticationContext(),
            tenantIdentifier,
            new RequestedClientId(clientId),
            new ClientInstanceIdentifier(id),
            requestAttributes);

    return toResponseEntity(response);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("client-id") String clientId,
      @PathVariable("id") String id,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    ClientInstanceManagementResponse response =
        clientInstanceManagementApi.delete(
            operatorPrincipal.authenticationContext(),
            tenantIdentifier,
            new RequestedClientId(clientId),
            new ClientInstanceIdentifier(id),
            requestAttributes,
            dryRun);

    return toResponseEntity(response);
  }

  private ResponseEntity<?> toResponseEntity(ClientInstanceManagementResponse response) {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}

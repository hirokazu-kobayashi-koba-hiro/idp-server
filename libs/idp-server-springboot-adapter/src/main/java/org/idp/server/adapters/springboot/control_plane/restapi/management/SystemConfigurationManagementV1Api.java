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
import org.idp.server.control_plane.management.system.SystemConfigurationManagementApi;
import org.idp.server.control_plane.management.system.io.SystemConfigurationManagementResponse;
import org.idp.server.control_plane.management.system.io.SystemConfigurationUpdateRequest;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * System configuration management REST API.
 *
 * <p>Provides endpoints for managing system-wide configuration settings such as SSRF protection and
 * trusted proxies. This is a system-level API that applies across all tenants.
 *
 * <h2>Endpoints</h2>
 *
 * <ul>
 *   <li>GET /v1/management/system-configurations - Retrieve current configuration
 *   <li>PUT /v1/management/system-configurations - Update configuration
 * </ul>
 */
@RestController
@RequestMapping("/v1/management/system-configurations")
public class SystemConfigurationManagementV1Api implements ParameterTransformable {

  SystemConfigurationManagementApi systemConfigurationManagementApi;

  public SystemConfigurationManagementV1Api(IdpServerApplication idpServerApplication) {
    this.systemConfigurationManagementApi = idpServerApplication.systemConfigurationManagementApi();
  }

  @GetMapping
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    SystemConfigurationManagementResponse response =
        systemConfigurationManagementApi.get(
            operatorPrincipal.authenticationContext(), requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PutMapping
  public ResponseEntity<?> put(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    SystemConfigurationManagementResponse response =
        systemConfigurationManagementApi.update(
            operatorPrincipal.authenticationContext(),
            new SystemConfigurationUpdateRequest(body),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}

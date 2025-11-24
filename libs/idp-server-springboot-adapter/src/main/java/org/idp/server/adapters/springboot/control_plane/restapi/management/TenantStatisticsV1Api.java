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
import org.idp.server.control_plane.management.statistics.TenantStatisticsApi;
import org.idp.server.control_plane.management.statistics.TenantStatisticsResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.TenantStatisticsQueries;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/management/tenants/{tenant-id}/statistics")
public class TenantStatisticsV1Api implements ParameterTransformable {

  TenantStatisticsApi tenantStatisticsApi;

  public TenantStatisticsV1Api(IdpServerApplication idpServerApplication) {
    this.tenantStatisticsApi = idpServerApplication.tenantStatisticsApi();
  }

  @GetMapping
  public ResponseEntity<?> getStatistics(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    TenantStatisticsQueries queries = new TenantStatisticsQueries(queryParams);

    TenantStatisticsResponse response =
        tenantStatisticsApi.findByDateRange(
            operatorPrincipal.authenticationContext(),
            tenantIdentifier,
            queries,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");

    return new ResponseEntity<>(
        response.toJson(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}

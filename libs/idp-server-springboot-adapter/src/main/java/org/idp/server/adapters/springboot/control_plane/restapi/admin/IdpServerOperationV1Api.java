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

package org.idp.server.adapters.springboot.control_plane.restapi.admin;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.control_plane.admin.operation.IdpServerOperationApi;
import org.idp.server.control_plane.admin.operation.io.IdpServerOperationRequest;
import org.idp.server.control_plane.admin.operation.io.IdpServerOperationResponse;
import org.idp.server.platform.multi_tenancy.tenant.AdminTenantContext;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin/operations")
public class IdpServerOperationV1Api implements ParameterTransformable {

  IdpServerOperationApi idpServerOperationApi;

  public IdpServerOperationV1Api(IdpServerApplication idpServerApplication) {
    this.idpServerOperationApi = idpServerApplication.idpServerOperationApi();
  }

  @PostMapping("/delete-expired-data")
  public ResponseEntity<?> post(
      @RequestBody(required = false) Map<String, Object> body,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    TenantIdentifier adminTenantIdentifier = AdminTenantContext.getTenantIdentifier();
    IdpServerOperationResponse response =
        idpServerOperationApi.deleteExpiredData(
            adminTenantIdentifier, new IdpServerOperationRequest(body), requestAttributes);

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), headers, HttpStatus.valueOf(response.statusCode()));
  }
}

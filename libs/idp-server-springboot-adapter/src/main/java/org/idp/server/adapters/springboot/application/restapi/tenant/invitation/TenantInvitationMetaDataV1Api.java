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


package org.idp.server.adapters.springboot.application.restapi.tenant.invitation;

import jakarta.servlet.http.HttpServletRequest;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationIdentifier;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationMetaDataApi;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationMetaDataResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/v1/invitations")
public class TenantInvitationMetaDataV1Api implements ParameterTransformable {

  TenantInvitationMetaDataApi tenantInvitationMetaDataApi;

  public TenantInvitationMetaDataV1Api(IdpServerApplication idpServerApplication) {
    this.tenantInvitationMetaDataApi = idpServerApplication.tenantInvitationMetaDataApi();
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") TenantInvitationIdentifier identifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    TenantInvitationMetaDataResponse response =
        tenantInvitationMetaDataApi.get(tenantIdentifier, identifier, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}

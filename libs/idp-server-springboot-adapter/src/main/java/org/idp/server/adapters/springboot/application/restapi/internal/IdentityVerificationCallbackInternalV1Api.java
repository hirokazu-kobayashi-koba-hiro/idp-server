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

package org.idp.server.adapters.springboot.application.restapi.internal;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.core.extension.identity.verification.IdentityVerificationCallbackApi;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.io.*;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/internal/v1/identity-verification/callback")
public class IdentityVerificationCallbackInternalV1Api implements ParameterTransformable {

  IdentityVerificationCallbackApi identityVerificationCallbackApi;

  public IdentityVerificationCallbackInternalV1Api(IdpServerApplication idpServerApplication) {
    this.identityVerificationCallbackApi = idpServerApplication.identityVerificationCallbackApi();
  }

  @PostMapping("/{verification-type}/{process}")
  public ResponseEntity<?> callback(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("verification-type") IdentityVerificationType verificationType,
      @PathVariable("process") IdentityVerificationProcess process,
      @RequestBody Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    IdentityVerificationCallbackResponse response =
        identityVerificationCallbackApi.callback(
            tenantIdentifier,
            verificationType,
            process,
            new IdentityVerificationRequest(requestBody),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.response(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/{verification-type}/{id}/{process}")
  public ResponseEntity<?> callback(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("verification-type") IdentityVerificationType verificationType,
      @PathVariable("id") IdentityVerificationApplicationIdentifier identifier,
      @PathVariable("process") IdentityVerificationProcess process,
      @RequestBody Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    IdentityVerificationCallbackResponse response =
        identityVerificationCallbackApi.callback(
            tenantIdentifier,
            identifier,
            verificationType,
            process,
            new IdentityVerificationRequest(requestBody),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.response(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}

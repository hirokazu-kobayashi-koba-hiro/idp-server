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

package org.idp.server.adapters.springboot.application.restapi.me;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.application.restapi.model.ResourceOwnerPrincipal;
import org.idp.server.core.extension.identity.verification.IdentityVerificationApplicationApi;
import org.idp.server.core.extension.identity.verification.IdentityVerificationApplicationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplicationQueries;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationApplicationResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/v1/me/identity-verification/applications")
public class IdentityVerificationApplicationV1Api implements ParameterTransformable {

  IdentityVerificationApplicationApi identityVerificationApplicationApi;

  public IdentityVerificationApplicationV1Api(IdpServerApplication idpServerApplication) {
    this.identityVerificationApplicationApi =
        idpServerApplication.identityVerificationApplicationApi();
  }

  @PostMapping("/{verification-type}/{verification-process}")
  public ResponseEntity<?> apply(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("verification-type") IdentityVerificationType verificationType,
      @PathVariable("verification-process") IdentityVerificationProcess identityVerificationProcess,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    IdentityVerificationApplicationResponse response =
        identityVerificationApplicationApi.apply(
            tenantIdentifier,
            resourceOwnerPrincipal.getUser(),
            resourceOwnerPrincipal.getOAuthToken(),
            verificationType,
            identityVerificationProcess,
            new IdentityVerificationApplicationRequest(requestBody),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.response(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @GetMapping
  public ResponseEntity<?> findList(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    IdentityVerificationApplicationResponse response =
        identityVerificationApplicationApi.findApplications(
            tenantIdentifier,
            resourceOwnerPrincipal.getUser(),
            resourceOwnerPrincipal.getOAuthToken(),
            new IdentityVerificationApplicationQueries(queryParams),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.response(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/{verification-type}/{id}/{verification-process}")
  public ResponseEntity<?> process(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") IdentityVerificationApplicationIdentifier identifier,
      @PathVariable("verification-type") IdentityVerificationType verificationType,
      @PathVariable("verification-process") IdentityVerificationProcess identityVerificationProcess,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    IdentityVerificationApplicationResponse response =
        identityVerificationApplicationApi.process(
            tenantIdentifier,
            resourceOwnerPrincipal.getUser(),
            resourceOwnerPrincipal.getOAuthToken(),
            identifier,
            verificationType,
            identityVerificationProcess,
            new IdentityVerificationApplicationRequest(requestBody),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.response(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/{id}/{verification-type}/evaluate-result")
  public ResponseEntity<?> callbackExamination(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("verification-type") IdentityVerificationType verificationType,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    IdentityVerificationApplicationResponse response =
        identityVerificationApplicationApi.evaluateResult(
            tenantIdentifier,
            resourceOwnerPrincipal.getUser(),
            resourceOwnerPrincipal.getOAuthToken(),
            verificationType,
            new IdentityVerificationApplicationRequest(requestBody),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.response(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @DeleteMapping("/{verification-type}/{id}")
  public ResponseEntity<?> delete(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") IdentityVerificationApplicationIdentifier identifier,
      @PathVariable("verification-type") IdentityVerificationType verificationType,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    IdentityVerificationApplicationResponse response =
        identityVerificationApplicationApi.delete(
            tenantIdentifier,
            resourceOwnerPrincipal.getUser(),
            resourceOwnerPrincipal.getOAuthToken(),
            identifier,
            verificationType,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.response(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}

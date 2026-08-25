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
import org.idp.server.adapters.springboot.application.restapi.FapiInteractionIdConfigurable;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.application.restapi.model.ResourceOwnerPrincipal;
import org.idp.server.core.extension.identity.verification.IdentityVerificationApi;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationResponse;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResultQueries;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/v1/me/identity-verification/results")
public class IdentityVerificationV1Api
    implements ParameterTransformable, FapiInteractionIdConfigurable {

  IdentityVerificationApi identityVerificationApi;

  public IdentityVerificationV1Api(IdpServerApplication idpServerApplication) {
    this.identityVerificationApi = idpServerApplication.identityVerificationApi();
  }

  @GetMapping
  public ResponseEntity<?> findList(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestHeader(required = false, value = "x-fapi-interaction-id") String fapiInteractionId,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    IdentityVerificationResponse response =
        identityVerificationApi.findList(
            tenantIdentifier,
            resourceOwnerPrincipal.getUser(),
            resourceOwnerPrincipal.getOAuthToken(),
            new IdentityVerificationResultQueries(queryParams),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    addFapiInteractionId(httpHeaders, fapiInteractionId);
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.response(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}

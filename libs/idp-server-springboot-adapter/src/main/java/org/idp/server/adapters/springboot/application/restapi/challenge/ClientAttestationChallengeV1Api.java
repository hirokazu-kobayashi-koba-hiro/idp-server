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

package org.idp.server.adapters.springboot.application.restapi.challenge;

import jakarta.servlet.http.HttpServletRequest;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallengeApi;
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallengeResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Challenge endpoint of draft-ietf-oauth-attestation-based-client-auth-10 Section 6.1.
 *
 * <p>Unauthenticated by design: it hands out an opaque nonce that the Client Instance carries in
 * the {@code challenge} claim of its next Client Attestation PoP JWT.
 */
@RestController
@RequestMapping("{tenant-id}/v1/client-attestation/challenges")
public class ClientAttestationChallengeV1Api implements ParameterTransformable {

  ClientAttestationChallengeApi clientAttestationChallengeApi;

  public ClientAttestationChallengeV1Api(IdpServerApplication idpServerApplication) {
    this.clientAttestationChallengeApi = idpServerApplication.clientAttestationChallengeApi();
  }

  @PostMapping
  public ResponseEntity<?> challenge(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    ClientAttestationChallengeResponse response =
        clientAttestationChallengeApi.issue(tenantIdentifier, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    // Section 6.1: the response MUST be uncacheable.
    httpHeaders.add("Cache-Control", "no-store");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}

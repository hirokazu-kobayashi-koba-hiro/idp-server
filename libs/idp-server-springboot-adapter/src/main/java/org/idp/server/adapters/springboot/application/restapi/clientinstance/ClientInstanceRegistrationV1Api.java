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

package org.idp.server.adapters.springboot.application.restapi.clientinstance;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceChallengeRequest;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegisterRequest;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationApi;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Client Instance registration for end-user applications.
 *
 * <p>Unauthenticated by design: registration has to happen before the client can authenticate at
 * the token endpoint, which is also where login happens. The platform attestation bound to the
 * server issued challenge is what authorizes the request.
 */
@RestController
@RequestMapping("{tenant-id}/v1/client-instances")
public class ClientInstanceRegistrationV1Api implements ParameterTransformable {

  ClientInstanceRegistrationApi clientInstanceRegistrationApi;

  public ClientInstanceRegistrationV1Api(IdpServerApplication idpServerApplication) {
    this.clientInstanceRegistrationApi = idpServerApplication.clientInstanceRegistrationApi();
  }

  @PostMapping("/challenges")
  public ResponseEntity<?> challenge(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestBody(required = false) Map<String, Object> body,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    ClientInstanceRegistrationResponse response =
        clientInstanceRegistrationApi.challenge(
            tenantIdentifier, new ClientInstanceChallengeRequest(body), requestAttributes);

    return toResponseEntity(response);
  }

  @PostMapping
  public ResponseEntity<?> register(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestBody(required = false) Map<String, Object> body,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    ClientInstanceRegistrationResponse response =
        clientInstanceRegistrationApi.register(
            tenantIdentifier, new ClientInstanceRegisterRequest(body), requestAttributes);

    return toResponseEntity(response);
  }

  private ResponseEntity<?> toResponseEntity(ClientInstanceRegistrationResponse response) {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    // Challenges must not be cached by intermediaries.
    httpHeaders.add("cache-control", "no-store");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}

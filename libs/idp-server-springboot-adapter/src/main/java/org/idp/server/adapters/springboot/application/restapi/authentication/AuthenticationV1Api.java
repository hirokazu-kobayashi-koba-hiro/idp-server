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

package org.idp.server.adapters.springboot.application.restapi.authentication;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.authentication.interactors.device.AuthenticationApi;
import org.idp.server.core.extension.ciba.CibaFlowApi;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.oidc.OAuthFlowApi;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(("{tenant-id}/v1/authentications"))
public class AuthenticationV1Api implements ParameterTransformable {

  AuthenticationApi authenticationApi;
  OAuthFlowApi oAuthFlowApi;
  CibaFlowApi cibaFlowApi;

  public AuthenticationV1Api(IdpServerApplication idpServerApplication) {
    this.authenticationApi = idpServerApplication.authenticationApi();
    this.oAuthFlowApi = idpServerApplication.oAuthFlowApi();
    this.cibaFlowApi = idpServerApplication.cibaFlowApi();
  }

  @PostMapping
  public ResponseEntity<?> post(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");

    return new ResponseEntity<>(httpHeaders, HttpStatus.OK);
  }

  @PostMapping("/{id}/{interaction-type}")
  public ResponseEntity<?> interact(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") AuthorizationIdentifier authorizationIdentifier,
      @PathVariable("interaction-type") AuthenticationInteractionType type,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    AuthenticationInteractionRequest request = new AuthenticationInteractionRequest(requestBody);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthenticationTransaction authenticationTransaction =
        authenticationApi.get(tenantIdentifier, authorizationIdentifier);
    AuthenticationInteractionRequestResult result =
        interact(
            tenantIdentifier,
            authorizationIdentifier,
            authenticationTransaction,
            type,
            request,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        result.response(), httpHeaders, HttpStatus.valueOf(result.statusCode()));
  }

  private AuthenticationInteractionRequestResult interact(
      TenantIdentifier tenantIdentifier,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationTransaction authenticationTransaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes) {
    switch (authenticationTransaction.flow()) {
      case OAUTH -> {
        return oAuthFlowApi.interact(
            tenantIdentifier,
            new AuthorizationRequestIdentifier(authorizationIdentifier.value()),
            type,
            request,
            requestAttributes);
      }

      case CIBA -> {
        return cibaFlowApi.interact(
            tenantIdentifier,
            new BackchannelAuthenticationRequestIdentifier(authorizationIdentifier.value()),
            type,
            request,
            requestAttributes);
      }
      default ->
          throw new UnSupportedException("Unexpected value: " + authenticationTransaction.flow());
    }
  }
}

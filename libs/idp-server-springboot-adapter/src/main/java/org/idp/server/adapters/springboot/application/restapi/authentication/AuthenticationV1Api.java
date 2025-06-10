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
import org.idp.server.core.extension.ciba.CibaFlowApi;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.oidc.OAuthFlowApi;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.authentication.AuthenticationApi;
import org.idp.server.core.oidc.authentication.io.AuthenticationTransactionFindingListResponse;
import org.idp.server.core.oidc.identity.UserOperationApi;
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
  UserOperationApi userOperationApi;

  public AuthenticationV1Api(IdpServerApplication idpServerApplication) {
    this.authenticationApi = idpServerApplication.authenticationApi();
    this.oAuthFlowApi = idpServerApplication.oAuthFlowApi();
    this.cibaFlowApi = idpServerApplication.cibaFlowApi();
    this.userOperationApi = idpServerApplication.userOperationApi();
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

  @GetMapping
  public ResponseEntity<?> findList(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthenticationTransactionFindingListResponse response =
        authenticationApi.findList(
            tenantIdentifier, new AuthenticationTransactionQueries(queryParams));

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/{id}/{interaction-type}")
  public ResponseEntity<?> interact(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      @PathVariable("interaction-type") AuthenticationInteractionType type,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    AuthenticationInteractionRequest request = new AuthenticationInteractionRequest(requestBody);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthenticationTransaction authenticationTransaction =
        authenticationApi.get(tenantIdentifier, authenticationTransactionIdentifier);
    AuthenticationInteractionRequestResult result =
        interact(tenantIdentifier, authenticationTransaction, type, request, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        result.response(), httpHeaders, HttpStatus.valueOf(result.statusCode()));
  }

  private AuthenticationInteractionRequestResult interact(
      TenantIdentifier tenantIdentifier,
      AuthenticationTransaction authenticationTransaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes) {

    switch (authenticationTransaction.flow()) {
      case OAUTH -> {
        return oAuthFlowApi.interact(
            tenantIdentifier,
            new AuthorizationRequestIdentifier(
                authenticationTransaction.authorizationIdentifier().value()),
            type,
            request,
            requestAttributes);
      }

      case CIBA -> {
        return cibaFlowApi.interact(
            tenantIdentifier,
            new BackchannelAuthenticationRequestIdentifier(
                authenticationTransaction.authorizationIdentifier().value()),
            authenticationTransaction,
            type,
            request,
            requestAttributes);
      }

      case FIDO_UAF_REGISTRATION -> {
        return userOperationApi.interact(
            tenantIdentifier,
            authenticationTransaction.identifier(),
            type,
            request,
            requestAttributes);
      }

      default ->
          throw new UnSupportedException("Unexpected value: " + authenticationTransaction.flow());
    }
  }
}

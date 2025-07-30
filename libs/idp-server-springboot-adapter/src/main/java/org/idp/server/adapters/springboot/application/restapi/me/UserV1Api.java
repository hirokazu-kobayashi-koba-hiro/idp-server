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
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserOperationApi;
import org.idp.server.core.oidc.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.oidc.identity.io.AuthenticationDevicePatchRequest;
import org.idp.server.core.oidc.identity.io.MfaRegistrationRequest;
import org.idp.server.core.oidc.identity.io.UserOperationResponse;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.type.AuthFlow;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/v1/me")
public class UserV1Api implements ParameterTransformable {

  UserOperationApi userOperationApi;

  public UserV1Api(IdpServerApplication idpServerApplication) {
    this.userOperationApi = idpServerApplication.userOperationApi();
  }

  @PostMapping("/mfa/{mfa-operation-type}")
  public ResponseEntity<?> requestMfaRegistration(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("mfa-operation-type") String mfaOperationType,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    User user = resourceOwnerPrincipal.getUser();
    OAuthToken oAuthToken = resourceOwnerPrincipal.getOAuthToken();
    MfaRegistrationRequest request = new MfaRegistrationRequest(requestBody);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthFlow authFlow = AuthFlow.of(mfaOperationType);
    UserOperationResponse response =
        userOperationApi.requestMfaOperation(
            tenantIdentifier, user, oAuthToken, authFlow, request, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PatchMapping("/authentication-devices/{device-id}")
  public ResponseEntity<?> patchAuthenticationDevice(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("device-id") AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    User user = resourceOwnerPrincipal.getUser();
    OAuthToken oAuthToken = resourceOwnerPrincipal.getOAuthToken();
    AuthenticationDevicePatchRequest request =
        AuthenticationDevicePatchRequest.fromMap(requestBody);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    UserOperationResponse response =
        userOperationApi.patchAuthenticationDevice(
            tenantIdentifier,
            user,
            oAuthToken,
            authenticationDeviceIdentifier,
            request,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @DeleteMapping
  public ResponseEntity<?> delete(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      HttpServletRequest httpServletRequest) {

    User user = resourceOwnerPrincipal.getUser();
    OAuthToken oAuthToken = resourceOwnerPrincipal.getOAuthToken();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    userOperationApi.delete(tenantIdentifier, user, oAuthToken, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(Map.of(), httpHeaders, HttpStatus.valueOf(204));
  }
}

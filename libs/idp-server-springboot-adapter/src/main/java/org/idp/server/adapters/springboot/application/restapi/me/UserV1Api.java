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
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserOperationApi;
import org.idp.server.core.openid.identity.authentication.PasswordChangeRequest;
import org.idp.server.core.openid.identity.authentication.PasswordChangeResponse;
import org.idp.server.core.openid.identity.authentication.PasswordResetRequest;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeIdentifier;
import org.idp.server.core.openid.identity.contact.ContactVerificationOperation;
import org.idp.server.core.openid.identity.contact.ContactVerificationRequest;
import org.idp.server.core.openid.identity.contact.ContactVerificationResponse;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.identity.io.AuthenticationDevicePatchRequest;
import org.idp.server.core.openid.identity.io.MfaRegistrationRequest;
import org.idp.server.core.openid.identity.io.UserOperationResponse;
import org.idp.server.core.openid.oauth.type.AuthFlow;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/v1/me")
public class UserV1Api implements ParameterTransformable, FapiInteractionIdConfigurable {

  UserOperationApi userOperationApi;

  public UserV1Api(IdpServerApplication idpServerApplication) {
    this.userOperationApi = idpServerApplication.userOperationApi();
  }

  @PostMapping("/mfa/{mfa-operation-type}")
  public ResponseEntity<?> requestMfaRegistration(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("mfa-operation-type") String mfaOperationType,
      @RequestHeader(required = false, value = "x-fapi-interaction-id") String fapiInteractionId,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    User user = resourceOwnerPrincipal.getUser();
    OAuthToken oAuthToken = resourceOwnerPrincipal.getOAuthToken();
    MfaRegistrationRequest request = new MfaRegistrationRequest(requestBody);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthFlow authFlow = new AuthFlow(mfaOperationType);
    UserOperationResponse response =
        userOperationApi.requestMfaOperation(
            tenantIdentifier, user, oAuthToken, authFlow, request, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    addFapiInteractionId(httpHeaders, fapiInteractionId);
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/email/verification")
  public ResponseEntity<?> requestEmailVerification(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestHeader(required = false, value = "x-fapi-interaction-id") String fapiInteractionId,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    return requestContact(
        resourceOwnerPrincipal,
        tenantIdentifier,
        ContactVerificationOperation.EMAIL_VERIFY,
        fapiInteractionId,
        requestBody,
        httpServletRequest);
  }

  @PostMapping("/email/verification/{id}/verify")
  public ResponseEntity<?> verifyEmailVerification(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") ContactVerificationChallengeIdentifier challengeIdentifier,
      @RequestHeader(required = false, value = "x-fapi-interaction-id") String fapiInteractionId,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    return verifyContact(
        resourceOwnerPrincipal,
        tenantIdentifier,
        ContactVerificationOperation.EMAIL_VERIFY,
        challengeIdentifier,
        fapiInteractionId,
        requestBody,
        httpServletRequest);
  }

  @PostMapping("/email/change")
  public ResponseEntity<?> requestEmailChange(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestHeader(required = false, value = "x-fapi-interaction-id") String fapiInteractionId,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    return requestContact(
        resourceOwnerPrincipal,
        tenantIdentifier,
        ContactVerificationOperation.EMAIL_CHANGE,
        fapiInteractionId,
        requestBody,
        httpServletRequest);
  }

  @PostMapping("/email/change/{id}/verify")
  public ResponseEntity<?> verifyEmailChange(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") ContactVerificationChallengeIdentifier challengeIdentifier,
      @RequestHeader(required = false, value = "x-fapi-interaction-id") String fapiInteractionId,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    return verifyContact(
        resourceOwnerPrincipal,
        tenantIdentifier,
        ContactVerificationOperation.EMAIL_CHANGE,
        challengeIdentifier,
        fapiInteractionId,
        requestBody,
        httpServletRequest);
  }

  @PostMapping("/phone/verification")
  public ResponseEntity<?> requestPhoneVerification(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestHeader(required = false, value = "x-fapi-interaction-id") String fapiInteractionId,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    return requestContact(
        resourceOwnerPrincipal,
        tenantIdentifier,
        ContactVerificationOperation.PHONE_VERIFY,
        fapiInteractionId,
        requestBody,
        httpServletRequest);
  }

  @PostMapping("/phone/verification/{id}/verify")
  public ResponseEntity<?> verifyPhoneVerification(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") ContactVerificationChallengeIdentifier challengeIdentifier,
      @RequestHeader(required = false, value = "x-fapi-interaction-id") String fapiInteractionId,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    return verifyContact(
        resourceOwnerPrincipal,
        tenantIdentifier,
        ContactVerificationOperation.PHONE_VERIFY,
        challengeIdentifier,
        fapiInteractionId,
        requestBody,
        httpServletRequest);
  }

  @PostMapping("/phone/change")
  public ResponseEntity<?> requestPhoneChange(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestHeader(required = false, value = "x-fapi-interaction-id") String fapiInteractionId,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    return requestContact(
        resourceOwnerPrincipal,
        tenantIdentifier,
        ContactVerificationOperation.PHONE_CHANGE,
        fapiInteractionId,
        requestBody,
        httpServletRequest);
  }

  @PostMapping("/phone/change/{id}/verify")
  public ResponseEntity<?> verifyPhoneChange(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") ContactVerificationChallengeIdentifier challengeIdentifier,
      @RequestHeader(required = false, value = "x-fapi-interaction-id") String fapiInteractionId,
      @RequestBody(required = false) Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    return verifyContact(
        resourceOwnerPrincipal,
        tenantIdentifier,
        ContactVerificationOperation.PHONE_CHANGE,
        challengeIdentifier,
        fapiInteractionId,
        requestBody,
        httpServletRequest);
  }

  private ResponseEntity<?> requestContact(
      ResourceOwnerPrincipal resourceOwnerPrincipal,
      TenantIdentifier tenantIdentifier,
      ContactVerificationOperation operation,
      String fapiInteractionId,
      Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    ContactVerificationResponse response =
        userOperationApi.requestContactVerification(
            tenantIdentifier,
            resourceOwnerPrincipal.getUser(),
            resourceOwnerPrincipal.getOAuthToken(),
            operation,
            new ContactVerificationRequest(requestBody),
            transform(httpServletRequest));

    return toResponseEntity(response, fapiInteractionId);
  }

  private ResponseEntity<?> verifyContact(
      ResourceOwnerPrincipal resourceOwnerPrincipal,
      TenantIdentifier tenantIdentifier,
      ContactVerificationOperation operation,
      ContactVerificationChallengeIdentifier challengeIdentifier,
      String fapiInteractionId,
      Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    ContactVerificationResponse response =
        userOperationApi.verifyContactVerification(
            tenantIdentifier,
            resourceOwnerPrincipal.getUser(),
            resourceOwnerPrincipal.getOAuthToken(),
            operation,
            challengeIdentifier,
            new ContactVerificationRequest(requestBody),
            transform(httpServletRequest));

    return toResponseEntity(response, fapiInteractionId);
  }

  private ResponseEntity<?> toResponseEntity(
      ContactVerificationResponse response, String fapiInteractionId) {
    HttpHeaders httpHeaders = new HttpHeaders();
    addFapiInteractionId(httpHeaders, fapiInteractionId);
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PatchMapping("/authentication-devices/{device-id}")
  public ResponseEntity<?> patchAuthenticationDevice(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("device-id") AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      @RequestHeader(required = false, value = "x-fapi-interaction-id") String fapiInteractionId,
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
    addFapiInteractionId(httpHeaders, fapiInteractionId);
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @DeleteMapping("/authentication-devices/{device-id}")
  public ResponseEntity<?> deleteAuthenticationDevice(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("device-id") AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      @RequestHeader(required = false, value = "x-fapi-interaction-id") String fapiInteractionId,
      HttpServletRequest httpServletRequest) {

    User user = resourceOwnerPrincipal.getUser();
    OAuthToken oAuthToken = resourceOwnerPrincipal.getOAuthToken();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    UserOperationResponse response =
        userOperationApi.deleteAuthenticationDevice(
            tenantIdentifier, user, oAuthToken, authenticationDeviceIdentifier, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    addFapiInteractionId(httpHeaders, fapiInteractionId);
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @DeleteMapping
  public ResponseEntity<?> delete(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestHeader(required = false, value = "x-fapi-interaction-id") String fapiInteractionId,
      HttpServletRequest httpServletRequest) {

    User user = resourceOwnerPrincipal.getUser();
    OAuthToken oAuthToken = resourceOwnerPrincipal.getOAuthToken();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    UserOperationResponse response =
        userOperationApi.delete(tenantIdentifier, user, oAuthToken, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    addFapiInteractionId(httpHeaders, fapiInteractionId);
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/password/change")
  public ResponseEntity<?> changePassword(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestHeader(required = false, value = "x-fapi-interaction-id") String fapiInteractionId,
      @RequestBody Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    User user = resourceOwnerPrincipal.getUser();
    OAuthToken oAuthToken = resourceOwnerPrincipal.getOAuthToken();
    PasswordChangeRequest request = new PasswordChangeRequest(requestBody);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    PasswordChangeResponse response =
        userOperationApi.changePassword(
            tenantIdentifier, user, oAuthToken, request, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    addFapiInteractionId(httpHeaders, fapiInteractionId);
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/password/reset")
  public ResponseEntity<?> resetPassword(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestHeader(required = false, value = "x-fapi-interaction-id") String fapiInteractionId,
      @RequestBody Map<String, Object> requestBody,
      HttpServletRequest httpServletRequest) {

    User user = resourceOwnerPrincipal.getUser();
    OAuthToken oAuthToken = resourceOwnerPrincipal.getOAuthToken();
    PasswordResetRequest request = new PasswordResetRequest(requestBody);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    PasswordChangeResponse response =
        userOperationApi.resetPassword(
            tenantIdentifier, user, oAuthToken, request, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    addFapiInteractionId(httpHeaders, fapiInteractionId);
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}

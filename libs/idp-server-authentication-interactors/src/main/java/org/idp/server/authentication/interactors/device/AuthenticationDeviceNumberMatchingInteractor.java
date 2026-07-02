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

package org.idp.server.authentication.interactors.device;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.exception.MfaTransactionNotFoundException;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Verifies the number-matching code for the authorization code flow FIDO step-up (push fatigue
 * mitigation, Issue #1505).
 *
 * <p>Unlike the CIBA {@link AuthenticationDeviceBindingMessageInteractor} — whose value comes from
 * the request context and IS exposed to the authenticated device (the device displays it) — the
 * number-matching code is generated and stored server-side by {@link
 * AuthenticationDeviceNumberMatchingChallengeInteractor} and is NEVER sent to the device. The user
 * reads it from the sign-in screen (SPA) and transcribes it into the device, which submits it here
 * for verification. Keeping the value out of the device-facing serialization is exactly what makes
 * number-matching resistant to push fatigue: the approver must look at the originating screen.
 */
public class AuthenticationDeviceNumberMatchingInteractor implements AuthenticationInteractor {

  AuthenticationInteractionQueryRepository interactionQueryRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(AuthenticationDeviceNumberMatchingInteractor.class);

  public AuthenticationDeviceNumberMatchingInteractor(
      AuthenticationInteractionQueryRepository interactionQueryRepository) {
    this.interactionQueryRepository = interactionQueryRepository;
  }

  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.AUTHENTICATION_DEVICE_NUMBER_MATCHING.toType();
  }

  @Override
  public boolean isBrowserBased() {
    return false;
  }

  @Override
  public String method() {
    return "number-matching";
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    try {
      log.debug("AuthenticationDeviceNumberMatchingInteractor called");

      String expectedCode = resolveExpectedCode(tenant, transaction);
      if (expectedCode == null) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "invalid_request");
        response.put("error_description", "number_matching_code has not been issued");

        return AuthenticationInteractionRequestResult.clientError(
            response,
            type,
            operationType(),
            method(),
            DefaultSecurityEventType.authentication_device_number_matching_failure);
      }

      String submittedCode = request.getValueAsString("number_matching_code");
      if (!expectedCode.equals(submittedCode)) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "invalid_request");
        response.put("error_description", "number_matching_code does not match");

        return AuthenticationInteractionRequestResult.clientError(
            response,
            type,
            operationType(),
            method(),
            DefaultSecurityEventType.authentication_device_number_matching_failure);
      }

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.SUCCESS,
          type,
          operationType(),
          method(),
          Map.of(),
          DefaultSecurityEventType.authentication_device_number_matching_success);
    } catch (IllegalArgumentException validationException) {
      // Issue #1008: Handle validation errors from getValueAsString()
      log.warn("Request validation failed: {}", validationException.getMessage());

      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", validationException.getMessage());

      return AuthenticationInteractionRequestResult.clientError(
          response,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.authentication_device_number_matching_failure);
    }
  }

  /**
   * Reads the number-matching code that {@link
   * AuthenticationDeviceNumberMatchingChallengeInteractor} generated and stored for this
   * transaction. Returns null when no challenge has been prepared.
   */
  private String resolveExpectedCode(Tenant tenant, AuthenticationTransaction transaction) {
    try {
      NumberMatchingChallenge stored =
          interactionQueryRepository.get(
              tenant,
              transaction.identifier(),
              "authentication-device-number-matching-challenge",
              NumberMatchingChallenge.class);
      return stored != null ? stored.code() : null;
    } catch (MfaTransactionNotFoundException notFound) {
      return null;
    }
  }
}

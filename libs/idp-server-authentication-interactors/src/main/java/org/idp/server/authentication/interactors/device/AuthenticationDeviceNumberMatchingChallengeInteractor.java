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
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Issues the number-matching code for the authorization code flow FIDO step-up (push fatigue
 * mitigation, Issue #1505).
 *
 * <p>Generation is intentionally separate from push delivery: push (FCM) stays in {@link
 * AuthenticationDeviceNotificationInteractor} (shared with CIBA, single FCM config), while this
 * interactor only generates the code, stores it server-side, and returns it for the sign-in screen
 * (SPA) to display. The code is NEVER sent to the device; the user transcribes it. {@link
 * AuthenticationDeviceNumberMatchingInteractor} later verifies the transcribed value against the
 * stored one. Length and character set are read from the {@code
 * authentication-device-number-matching} configuration (with defaults) since they are
 * requirement-dependent.
 */
public class AuthenticationDeviceNumberMatchingChallengeInteractor
    implements AuthenticationInteractor {

  static final String CONFIG_KEY = "authentication-device-number-matching";

  /**
   * Storage key under which the issued code is persisted for this transaction. {@link
   * AuthenticationDeviceNumberMatchingInteractor} reads it back with the same key, so both sides
   * share this constant instead of duplicating the literal.
   */
  static final String CHALLENGE_INTERACTION_KEY = "authentication-device-number-matching-challenge";

  static final int DEFAULT_LENGTH = 4;

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  AuthenticationInteractionCommandRepository interactionCommandRepository;
  LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationDeviceNumberMatchingChallengeInteractor.class);

  public AuthenticationDeviceNumberMatchingChallengeInteractor(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      AuthenticationInteractionCommandRepository interactionCommandRepository) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.interactionCommandRepository = interactionCommandRepository;
  }

  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.AUTHENTICATION_DEVICE_NUMBER_MATCHING_CHALLENGE
        .toType();
  }

  @Override
  public OperationType operationType() {
    return OperationType.CHALLENGE;
  }

  @Override
  public boolean isBrowserBased() {
    return false;
  }

  @Override
  public String method() {
    return CHALLENGE_INTERACTION_KEY;
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
      log.debug("AuthenticationDeviceNumberMatchingChallengeInteractor called");

      Map<String, Object> details = resolveDetails(tenant);
      int configuredLength =
          (details.get("length") instanceof Number number) ? number.intValue() : DEFAULT_LENGTH;
      // Guard against a misconfigured non-positive length: a zero-length code would be an empty
      // string that any empty submission matches, so fall back to the default rather than issue a
      // degenerate code.
      int length = configuredLength < 1 ? DEFAULT_LENGTH : configuredLength;

      String code = NumberMatchingCodeGenerator.generate(length);
      interactionCommandRepository.register(
          tenant, transaction.identifier(), method(), new NumberMatchingChallenge(code));

      // The code is returned ONLY for the sign-in screen (SPA) to display; it is never sent to the
      // device. Keeping it out of any device-facing payload is what makes number-matching resistant
      // to push fatigue (the approver must read it off the originating screen).
      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.SUCCESS,
          type,
          operationType(),
          method(),
          Map.of("number_matching_code", code),
          DefaultSecurityEventType.authentication_device_number_matching_challenge_success);
    } catch (Exception e) {
      log.error("Failed to issue number-matching code", e);
      Map<String, Object> response = new HashMap<>();
      response.put("error", "server_error");
      response.put("error_description", "Failed to issue number-matching code");
      return AuthenticationInteractionRequestResult.serverError(
          response,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.authentication_device_number_matching_challenge_failure);
    }
  }

  /**
   * Reads the number-matching config details, returning an empty map (→ defaults) when no config is
   * registered for the tenant so challenge issuance never depends on config provisioning.
   */
  private Map<String, Object> resolveDetails(Tenant tenant) {
    AuthenticationConfiguration configuration =
        configurationQueryRepository.find(tenant, CONFIG_KEY);
    if (!configuration.exists()) {
      return Map.of();
    }
    AuthenticationInteractionConfig interactionConfig =
        configuration.getAuthenticationConfig(CONFIG_KEY);
    if (interactionConfig == null) {
      return Map.of();
    }
    return interactionConfig.execution().details();
  }
}

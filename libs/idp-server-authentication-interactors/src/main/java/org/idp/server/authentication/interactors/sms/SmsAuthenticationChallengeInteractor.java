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

package org.idp.server.authentication.interactors.sms;

import java.util.Map;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.exception.UserTooManyFoundResultException;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class SmsAuthenticationChallengeInteractor implements AuthenticationInteractor {

  SmsAuthenticationExecutors executors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;

  public SmsAuthenticationChallengeInteractor(
      SmsAuthenticationExecutors executors,
      AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.executors = executors;
    this.configurationQueryRepository = configurationQueryRepository;
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      UserQueryRepository userQueryRepository) {
    try {
      SmsAuthenticationConfiguration configuration =
          configurationQueryRepository.get(tenant, "sms", SmsAuthenticationConfiguration.class);
      SmsAuthenticationExecutor executor = executors.get(configuration.type());

      // TODO dynamic specify provider
      User user =
          userQueryRepository.findByPhone(
              tenant, request.getValueAsString("phone_number"), "idp-server");

      if (!user.exists()) {
        Map<String, Object> response =
            Map.of("error", "invalid_request", "error_description", "user not found");
        return AuthenticationInteractionRequestResult.clientError(
            response, type, DefaultSecurityEventType.sms_verification_challenge_failure);
      }

      SmsAuthenticationExecutionRequest executionRequest =
          new SmsAuthenticationExecutionRequest(request.toMap());
      SmsAuthenticationExecutionResult executionResult =
          executor.challenge(tenant, transaction.identifier(), executionRequest, configuration);

      if (executionResult.isClientError()) {
        return AuthenticationInteractionRequestResult.clientError(
            executionResult.contents(),
            type,
            DefaultSecurityEventType.sms_verification_challenge_failure);
      }

      if (executionResult.isServerError()) {
        return AuthenticationInteractionRequestResult.serverError(
            executionResult.contents(),
            type,
            DefaultSecurityEventType.sms_verification_challenge_failure);
      }

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.SUCCESS,
          type,
          user,
          new Authentication(),
          executionResult.contents(),
          DefaultSecurityEventType.sms_verification_challenge_success);
    } catch (UserTooManyFoundResultException tooManyFoundResultException) {

      Map<String, Object> response =
          Map.of(
              "error",
              "invalid_request",
              "error_description",
              "too many users found for phone number: " + request.getValueAsString("phone_number"));
      return AuthenticationInteractionRequestResult.clientError(
          response, type, DefaultSecurityEventType.sms_verification_challenge_failure);
    }
  }
}

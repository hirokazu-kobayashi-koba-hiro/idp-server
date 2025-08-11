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

package org.idp.server.authentication.interactors.webauthn;

import org.idp.server.authentication.interactors.AuthenticationExecutionRequest;
import org.idp.server.authentication.interactors.AuthenticationExecutionResult;
import org.idp.server.authentication.interactors.AuthenticationExecutor;
import org.idp.server.authentication.interactors.AuthenticationExecutors;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class WebAuthnRegistrationChallengeInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationRepository;
  AuthenticationExecutors authenticationExecutors;
  LoggerWrapper log = LoggerWrapper.getLogger(WebAuthnRegistrationChallengeInteractor.class);

  public WebAuthnRegistrationChallengeInteractor(
      AuthenticationConfigurationQueryRepository configurationRepository,
      AuthenticationExecutors authenticationExecutors) {
    this.configurationRepository = configurationRepository;
    this.authenticationExecutors = authenticationExecutors;
  }

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.WEBAUTHN_REGISTRATION_CHALLENGE.toType();
  }

  @Override
  public OperationType operationType() {
    return OperationType.CHALLENGE;
  }

  @Override
  public String method() {
    return StandardAuthenticationMethod.WEB_AUTHN.type();
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    log.debug("WebAuthnRegistrationChallengeInteractor called");

    AuthenticationConfiguration configuration = configurationRepository.get(tenant, "webauthn");
    AuthenticationInteractionConfig authenticationInteractionConfig =
        configuration.getAuthenticationConfig("webauthn-registration-challenge");
    AuthenticationExecutionConfig execution = authenticationInteractionConfig.execution();

    AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

    AuthenticationExecutionRequest authenticationExecutionRequest =
        new AuthenticationExecutionRequest(request.toMap());
    AuthenticationExecutionResult executionResult =
        executor.execute(
            tenant,
            transaction.identifier(),
            authenticationExecutionRequest,
            requestAttributes,
            execution);

    if (executionResult.isClientError()) {
      return AuthenticationInteractionRequestResult.clientError(
          executionResult.contents(),
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.webauthn_registration_challenge_failure);
    }

    if (executionResult.isServerError()) {
      return AuthenticationInteractionRequestResult.serverError(
          executionResult.contents(),
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.webauthn_registration_challenge_failure);
    }

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        transaction.user(),
        executionResult.contents(),
        DefaultSecurityEventType.webauthn_registration_challenge_success);
  }
}

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

package org.idp.server.authentication.interactors.fidouaf;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.authentication.interactors.AuthenticationExecutionRequest;
import org.idp.server.authentication.interactors.AuthenticationExecutionResult;
import org.idp.server.authentication.interactors.AuthenticationExecutor;
import org.idp.server.authentication.interactors.AuthenticationExecutors;
import org.idp.server.authentication.interactors.fidouaf.plugin.FidoUafAdditionalRequestResolvers;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.oidc.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.oidc.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.oidc.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class FidoUafRegistrationChallengeInteractor implements AuthenticationInteractor {

  AuthenticationExecutors authenticationExecutors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  AuthenticationInteractionCommandRepository authenticationInteractionCommandRepository;
  FidoUafAdditionalRequestResolvers additionalRequestResolvers;
  LoggerWrapper log = LoggerWrapper.getLogger(FidoUafRegistrationChallengeInteractor.class);

  public FidoUafRegistrationChallengeInteractor(
      AuthenticationExecutors authenticationExecutors,
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      AuthenticationInteractionCommandRepository authenticationInteractionCommandRepository,
      FidoUafAdditionalRequestResolvers additionalRequestResolvers) {
    this.authenticationExecutors = authenticationExecutors;
    this.configurationQueryRepository = configurationQueryRepository;
    this.authenticationInteractionCommandRepository = authenticationInteractionCommandRepository;
    this.additionalRequestResolvers = additionalRequestResolvers;
  }

  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.FIDO_UAF_REGISTRATION_CHALLENGE.toType();
  }

  @Override
  public OperationType operationType() {
    return OperationType.CHALLENGE;
  }

  @Override
  public String method() {
    return StandardAuthenticationMethod.FIDO_UAF.type();
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    log.debug("FidoUafRegistrationChallengeInteractor called");

    AuthenticationConfiguration configuration =
        configurationQueryRepository.get(tenant, "fido-uaf");
    AuthenticationInteractionConfig authenticationInteractionConfig =
        configuration.getAuthenticationConfig("fido-uaf-registration-challenge");
    AuthenticationExecutionConfig execution = authenticationInteractionConfig.execution();
    AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

    AuthenticationDeviceIdentifier authenticationDeviceIdentifier =
        AuthenticationDeviceIdentifier.generate();

    // TODO
    Map<String, Object> executionRequest =
        new HashMap<>(Map.of("device_id", authenticationDeviceIdentifier.value()));
    Map<String, Object> additionalRequests =
        additionalRequestResolvers.resolveAll(tenant, type, request, transaction);
    executionRequest.putAll(additionalRequests);

    AuthenticationExecutionRequest authenticationExecutionRequest =
        new AuthenticationExecutionRequest(executionRequest);
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
          DefaultSecurityEventType.fido_uaf_registration_challenge_failure);
    }

    if (executionResult.isServerError()) {
      return AuthenticationInteractionRequestResult.serverError(
          executionResult.contents(),
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.fido_uaf_registration_challenge_failure);
    }

    FidoUafRegistrationInteraction fidoUafRegistrationInteraction =
        new FidoUafRegistrationInteraction(authenticationDeviceIdentifier.value());
    authenticationInteractionCommandRepository.register(
        tenant, transaction.identifier(), "fido-uaf", fidoUafRegistrationInteraction);

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        transaction.user(),
        executionResult.contents(),
        DefaultSecurityEventType.fido_uaf_registration_challenge_success);
  }
}

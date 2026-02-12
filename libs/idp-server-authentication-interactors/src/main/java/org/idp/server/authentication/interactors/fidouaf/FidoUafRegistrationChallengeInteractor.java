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
import org.idp.server.authentication.interactors.fidouaf.plugin.FidoUafAdditionalRequestResolvers;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationResponseConfig;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionRequest;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionResult;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutor;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutors;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.authentication.policy.AuthenticationResultConditionConfig;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
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
  public boolean isBrowserBased() {
    return false;
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

    // Verify user is authenticated
    if (!transaction.hasUser()) {
      log.warn(
          "FIDO-UAF registration challenge rejected: no authenticated user in transaction, tenant={}",
          tenant.identifier().value());

      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "unauthorized");
      errorResponse.put(
          "error_description", "User must be authenticated before registering a FIDO-UAF device.");

      return AuthenticationInteractionRequestResult.clientError(
          errorResponse,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.fido_uaf_registration_challenge_failure);
    }

    // Verify device registration ACR/MFA requirements
    if (!isDeviceRegistrationPolicyMet(tenant, transaction)) {
      log.warn(
          "FIDO-UAF device registration policy check failed: user={}, tenant={}",
          transaction.user().sub(),
          tenant.identifier().value());

      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "forbidden");
      errorResponse.put(
          "error_description",
          "Current authentication level does not meet device registration requirements. "
              + "Please complete required authentication steps (e.g., MFA or existing device authentication).");

      return AuthenticationInteractionRequestResult.clientError(
          errorResponse,
          type,
          operationType(),
          method(),
          transaction.user(),
          DefaultSecurityEventType.fido_uaf_registration_challenge_failure);
    }

    AuthenticationConfiguration configuration =
        configurationQueryRepository.get(tenant, "fido-uaf");
    AuthenticationInteractionConfig authenticationInteractionConfig =
        configuration.getAuthenticationConfig("fido-uaf-registration-challenge");
    AuthenticationExecutionConfig execution = authenticationInteractionConfig.execution();
    AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

    AuthenticationDeviceIdentifier authenticationDeviceIdentifier =
        AuthenticationDeviceIdentifier.generate();

    // TODO to be more flexible
    // https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/298
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

    AuthenticationResponseConfig responseConfig = authenticationInteractionConfig.response();
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(executionResult.contents());
    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> contents =
        MappingRuleObjectMapper.execute(responseConfig.bodyMappingRules(), jsonPathWrapper);

    if (!executionResult.isSuccess()) {

      log.warn(
          "FIDO-UAF registration challenge failed. status={}, contents={}",
          executionResult.statusCode(),
          executionResult.contents());

      return AuthenticationInteractionRequestResult.error(
          executionResult.statusCode(),
          contents,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.fido_uaf_registration_challenge_failure);
    }

    FidoUafInteraction fidoUafInteraction =
        new FidoUafInteraction(authenticationDeviceIdentifier.value());
    authenticationInteractionCommandRepository.register(
        tenant, transaction.identifier(), "fido-uaf-registration-challenge", fidoUafInteraction);

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        transaction.user(),
        contents,
        DefaultSecurityEventType.fido_uaf_registration_challenge_success);
  }

  /**
   * Checks if device registration policy requirements are met.
   *
   * <p>Ensures that FIDO-UAF device registration requires authentication meeting configured policy,
   * typically either:
   *
   * <ul>
   *   <li>Authentication with existing FIDO-UAF device, OR
   *   <li>Multi-factor authentication (password + TOTP)
   * </ul>
   *
   * @param tenant the tenant
   * @param transaction the authentication transaction
   * @return true if policy is met or not configured, false if policy check fails
   */
  private boolean isDeviceRegistrationPolicyMet(
      Tenant tenant, AuthenticationTransaction transaction) {

    AuthenticationPolicy authPolicy = transaction.authenticationPolicy();

    // Only verify if device_registration_conditions is configured
    if (!authPolicy.hasDeviceRegistrationConditions()) {
      log.debug(
          "No device_registration_conditions configured, skipping ACR verification for FIDO-UAF registration");
      return true;
    }

    AuthenticationInteractionResults interactionResults = transaction.interactionResults();
    AuthenticationResultConditionConfig conditions = authPolicy.deviceRegistrationConditions();

    // Evaluate conditions using existing MfaConditionEvaluator
    boolean satisfied =
        org.idp.server.core.openid.authentication.evaluator.MfaConditionEvaluator
            .isSuccessSatisfied(conditions, interactionResults);

    if (satisfied) {
      log.info(
          "FIDO-UAF device registration policy check passed: user={}, tenant={}",
          transaction.user().sub(),
          tenant.identifier().value());
    }

    return satisfied;
  }
}

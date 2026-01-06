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
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class FidoUafAuthenticationInteractor implements AuthenticationInteractor {

  AuthenticationExecutors authenticationExecutors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository;
  FidoUafAdditionalRequestResolvers additionalRequestResolvers;
  LoggerWrapper log = LoggerWrapper.getLogger(FidoUafAuthenticationInteractor.class);

  public FidoUafAuthenticationInteractor(
      AuthenticationExecutors authenticationExecutors,
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository,
      FidoUafAdditionalRequestResolvers additionalRequestResolvers) {
    this.authenticationExecutors = authenticationExecutors;
    this.configurationQueryRepository = configurationQueryRepository;
    this.authenticationInteractionQueryRepository = authenticationInteractionQueryRepository;
    this.additionalRequestResolvers = additionalRequestResolvers;
  }

  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.FIDO_UAF_AUTHENTICATION.toType();
  }

  @Override
  public boolean isBrowserBased() {
    return false;
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

    log.debug("FidoUafAuthenticationInteractor called");

    AuthenticationConfiguration configuration =
        configurationQueryRepository.get(tenant, "fido-uaf");
    AuthenticationInteractionConfig authenticationInteractionConfig =
        configuration.getAuthenticationConfig("fido-uaf-authentication");
    AuthenticationExecutionConfig execution = authenticationInteractionConfig.execution();
    AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

    Map<String, Object> executionRequest = new HashMap<>(request.toMap());
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

    if (executionResult.isClientError()) {

      log.warn("FIDO-UAF authentication failed. Client error: {}", executionResult.contents());

      return AuthenticationInteractionRequestResult.clientError(
          contents,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.fido_uaf_authentication_failure);
    }

    if (executionResult.isServerError()) {

      log.warn("FIDO-UAF authentication failed. Server error: {}", executionResult.contents());

      return AuthenticationInteractionRequestResult.serverError(
          contents,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.fido_uaf_authentication_failure);
    }

    FidoUafInteraction interaction =
        authenticationInteractionQueryRepository.get(
            tenant,
            transaction.identifier(),
            "fido-uaf-authentication-challenge",
            FidoUafInteraction.class);
    String deviceId = interaction.deviceId();
    User user = userQueryRepository.findByAuthenticationDevice(tenant, deviceId);

    if (!user.exists()) {

      log.warn("FIDO-UAF user resolution failed. deviceId: {}", deviceId);

      Map<String, Object> userErrorContents = new HashMap<>();
      userErrorContents.put("error", "invalid_request");
      userErrorContents.put(
          "error_description",
          String.format(
              "FIDO-UAF authentication succeeded but user could not be resolved. device ID: %s",
              deviceId));

      return AuthenticationInteractionRequestResult.clientError(
          userErrorContents,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.fido_uaf_authentication_failure);
    }

    log.debug("FIDO-UAF authentication succeeded for user: {}", user.sub());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        user,
        contents,
        DefaultSecurityEventType.fido_uaf_authentication_success);
  }
}

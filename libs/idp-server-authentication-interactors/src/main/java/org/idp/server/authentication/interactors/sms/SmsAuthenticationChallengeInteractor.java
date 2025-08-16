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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.authentication.interactors.AuthenticationExecutionRequest;
import org.idp.server.authentication.interactors.AuthenticationExecutionResult;
import org.idp.server.authentication.interactors.AuthenticationExecutor;
import org.idp.server.authentication.interactors.AuthenticationExecutors;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationResponseConfig;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.exception.UserTooManyFoundResultException;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class SmsAuthenticationChallengeInteractor implements AuthenticationInteractor {

  AuthenticationExecutors executors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(SmsAuthenticationChallengeInteractor.class);

  public SmsAuthenticationChallengeInteractor(
      AuthenticationExecutors executors,
      AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.executors = executors;
    this.configurationQueryRepository = configurationQueryRepository;
  }

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.SMS_AUTHENTICATION_CHALLENGE.toType();
  }

  @Override
  public OperationType operationType() {
    return OperationType.CHALLENGE;
  }

  @Override
  public String method() {
    return StandardAuthenticationMethod.SMS.type();
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

      log.debug("SmsAuthenticationChallengeInteractor called");

      AuthenticationConfiguration configuration = configurationQueryRepository.get(tenant, "sms");
      AuthenticationInteractionConfig authenticationInteractionConfig =
          configuration.getAuthenticationConfig("sms-authentication-challenge");
      AuthenticationExecutionConfig executionConfig = authenticationInteractionConfig.execution();

      String phoneNumber = resolvePhoneNumber(transaction, request);

      if (phoneNumber.isEmpty()) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "invalid_request");
        response.put("error_description", "phoneNumber is unspecified.");

        return new AuthenticationInteractionRequestResult(
            AuthenticationInteractionStatus.CLIENT_ERROR,
            type,
            operationType(),
            method(),
            response,
            DefaultSecurityEventType.sms_verification_challenge_failure);
      }

      String providerId = request.optValueAsString("provider_id", "idp-server");
      User user = resolveUser(tenant, transaction, phoneNumber, providerId, userQueryRepository);

      AuthenticationExecutor executor = executors.get(executionConfig.function());

      AuthenticationExecutionRequest executionRequest =
          new AuthenticationExecutionRequest(request.toMap());
      AuthenticationExecutionResult executionResult =
          executor.execute(
              tenant,
              transaction.identifier(),
              executionRequest,
              requestAttributes,
              executionConfig);

      AuthenticationResponseConfig responseConfig = authenticationInteractionConfig.response();
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(executionResult.contents());
      JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
      Map<String, Object> contents =
          MappingRuleObjectMapper.execute(responseConfig.bodyMappingRules(), jsonPathWrapper);

      if (executionResult.isClientError()) {
        return AuthenticationInteractionRequestResult.clientError(
            contents,
            type,
            operationType(),
            method(),
            DefaultSecurityEventType.sms_verification_challenge_failure);
      }

      if (executionResult.isServerError()) {
        return AuthenticationInteractionRequestResult.serverError(
            contents,
            type,
            operationType(),
            method(),
            DefaultSecurityEventType.sms_verification_challenge_failure);
      }

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.SUCCESS,
          type,
          operationType(),
          method(),
          user,
          contents,
          DefaultSecurityEventType.sms_verification_challenge_success);
    } catch (UserTooManyFoundResultException tooManyFoundResultException) {

      Map<String, Object> response =
          Map.of(
              "error",
              "invalid_request",
              "error_description",
              "too many users found for phone number: " + request.getValueAsString("phone_number"));
      return AuthenticationInteractionRequestResult.clientError(
          response,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.sms_verification_challenge_failure);
    }
  }

  private User resolveUser(
      Tenant tenant,
      AuthenticationTransaction transaction,
      String phoneNumber,
      String providerId,
      UserQueryRepository userQueryRepository) {

    if (transaction.hasUser()) {
      User user = transaction.user();
      user.setPhoneNumber(phoneNumber);
      return user;
    }

    User existingUser = userQueryRepository.findByPhone(tenant, phoneNumber, providerId);
    if (existingUser.exists()) {
      return existingUser;
    }

    User user = new User();
    String id = UUID.randomUUID().toString();
    user.setSub(id);
    user.setExternalUserId(id);
    user.setPhoneNumber(phoneNumber);

    return user;
  }

  private String resolvePhoneNumber(
      AuthenticationTransaction transaction, AuthenticationInteractionRequest request) {
    if (request.containsKey("phone_number")) {
      return request.getValueAsString("phone_number");
    }
    if (transaction.hasUser()) {
      User user = transaction.user();
      return user.phoneNumber();
    }
    return "";
  }
}

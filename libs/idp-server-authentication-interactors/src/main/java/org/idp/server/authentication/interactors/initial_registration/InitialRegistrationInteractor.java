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

package org.idp.server.authentication.interactors.initial_registration;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationRequestConfig;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.IdPUserCreator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.json.schema.JsonSchemaValidator;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class InitialRegistrationInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  PasswordEncodeDelegation passwordEncodeDelegation;
  JsonConverter jsonConverter;
  LoggerWrapper log = LoggerWrapper.getLogger(InitialRegistrationInteractor.class);

  public InitialRegistrationInteractor(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.INITIAL_REGISTRATION.toType();
  }

  @Override
  public OperationType operationType() {
    return OperationType.REGISTRATION;
  }

  @Override
  public String method() {
    return "initial-registration";
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    log.debug("InitialRegistrationInteractor called");

    AuthenticationConfiguration configuration =
        configurationQueryRepository.get(tenant, "initial-registration");
    AuthenticationInteractionConfig authenticationInteractionConfig =
        configuration.getAuthenticationConfig("initial-registration");
    AuthenticationRequestConfig requestConfig = authenticationInteractionConfig.request();

    JsonSchemaDefinition jsonSchemaDefinition = requestConfig.requestSchemaAsDefinition();
    JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(jsonSchemaDefinition);
    JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(request.toMap());
    JsonSchemaValidationResult validationResult = jsonSchemaValidator.validate(jsonNodeWrapper);

    if (!validationResult.isValid()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "invalid request.");
      response.put("error_details", validationResult.errors());

      return AuthenticationInteractionRequestResult.clientError(
          response, type, operationType(), method(), DefaultSecurityEventType.user_signup_failure);
    }

    String email = request.optValueAsString("email", "");
    String providerId = request.optValueAsString("provider_id", "idp-server");
    User existingUser = userQueryRepository.findByEmail(tenant, email, providerId);

    if (existingUser.exists()) {

      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "user is conflict with username and password");

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.CLIENT_ERROR,
          type,
          operationType(),
          method(),
          existingUser,
          response,
          DefaultSecurityEventType.user_signup_conflict);
    }

    IdPUserCreator idPUserCreator =
        new IdPUserCreator(jsonSchemaDefinition, request, passwordEncodeDelegation);
    User user = idPUserCreator.create();

    Map<String, Object> response = new HashMap<>();
    response.put("user", user.toMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        user,
        response,
        DefaultSecurityEventType.user_signup);
  }
}

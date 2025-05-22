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


package org.idp.server.authentication.interactors.password;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.basic.json.schema.JsonSchemaValidator;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.identity.IdPUserCreator;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class PasswordRegistrationInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  PasswordEncodeDelegation passwordEncodeDelegation;
  JsonConverter jsonConverter;

  public PasswordRegistrationInteractor(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      AuthenticationTransaction transaction,
      UserQueryRepository userQueryRepository) {

    Map json = configurationQueryRepository.get(tenant, "password", Map.class);
    JsonNodeWrapper definition = jsonConverter.readTree(json);
    JsonSchemaDefinition jsonSchemaDefinition = new JsonSchemaDefinition(definition);
    JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(jsonSchemaDefinition);
    JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(request.toMap());
    JsonSchemaValidationResult validationResult = jsonSchemaValidator.validate(jsonNodeWrapper);

    if (!validationResult.isValid()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "invalid request.");
      response.put("error_details", validationResult.errors());

      return AuthenticationInteractionRequestResult.clientError(
          response, type, DefaultSecurityEventType.user_signup_failure);
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
          existingUser,
          new Authentication(),
          response,
          DefaultSecurityEventType.user_signup_conflict);
    }

    IdPUserCreator idPUserCreator =
        new IdPUserCreator(jsonSchemaDefinition, request, passwordEncodeDelegation);
    User user = idPUserCreator.create();

    Authentication authentication = new Authentication();

    Map<String, Object> response = new HashMap<>();
    response.put("user", user.toMap());
    response.put("authentication", authentication.toMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        user,
        authentication,
        response,
        DefaultSecurityEventType.user_signup);
  }
}

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
import org.idp.server.core.openid.identity.authentication.PasswordPolicyValidationResult;
import org.idp.server.core.openid.identity.authentication.PasswordPolicyValidator;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.json.schema.JsonSchemaValidator;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;
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
    return OperationType.AUTHENTICATION;
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

    // Get registration schema (custom config or default)
    JsonSchemaDefinition jsonSchemaDefinition = getRegistrationSchema(tenant);
    JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(jsonSchemaDefinition);
    JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(request.toMap());
    JsonSchemaValidationResult validationResult = jsonSchemaValidator.validate(jsonNodeWrapper);

    if (!validationResult.isValid()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "invalid request.");
      response.put("error_messages", validationResult.errors());

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

      return AuthenticationInteractionRequestResult.clientError(
          response, type, operationType(), method(), DefaultSecurityEventType.user_signup_conflict);
    }

    // Validate password against tenant password policy
    if (request.containsKey("password")) {
      try {
        String password = request.getValueAsString("password");
        log.debug("Applying tenant password policy for initial registration");
        TenantIdentityPolicy identityPolicy = tenant.identityPolicyConfig();
        PasswordPolicyValidator passwordPolicy =
            new PasswordPolicyValidator(identityPolicy.passwordPolicyConfig());
        PasswordPolicyValidationResult passwordValidationResult = passwordPolicy.validate(password);

        if (passwordValidationResult.isInvalid()) {
          log.info(
              "Initial registration failed: password policy violation - {}",
              passwordValidationResult.errorMessage());
          Map<String, Object> response = new HashMap<>();
          response.put("error", "invalid_request");
          response.put("error_description", passwordValidationResult.errorMessage());

          return AuthenticationInteractionRequestResult.clientError(
              response,
              type,
              operationType(),
              method(),
              DefaultSecurityEventType.user_signup_failure);
        }
        log.debug("Password policy validation succeeded for initial registration");
      } catch (IllegalArgumentException validationException) {
        // Issue #1008: Handle validation errors from getValueAsString()
        log.warn("Password validation failed: {}", validationException.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("error", "invalid_request");
        response.put("error_description", validationException.getMessage());

        return AuthenticationInteractionRequestResult.clientError(
            response,
            type,
            operationType(),
            method(),
            DefaultSecurityEventType.user_signup_failure);
      }
    }

    IdPUserCreator idPUserCreator =
        new IdPUserCreator(jsonSchemaDefinition, request, passwordEncodeDelegation);
    User user = idPUserCreator.create();
    user.applyIdentityPolicy(tenant.identityPolicyConfig());

    Map<String, Object> response = new HashMap<>();
    response.put("user", user.toMinimalizedMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        user,
        response,
        DefaultSecurityEventType.user_signup);
  }

  /**
   * Get registration schema from tenant configuration, or use default schema.
   *
   * @param tenant tenant
   * @return registration schema definition
   */
  private JsonSchemaDefinition getRegistrationSchema(Tenant tenant) {
    AuthenticationConfiguration configuration =
        configurationQueryRepository.find(tenant, "initial-registration");

    if (configuration.exists()) {
      AuthenticationInteractionConfig authenticationInteractionConfig =
          configuration.getAuthenticationConfig("initial-registration");
      AuthenticationRequestConfig requestConfig = authenticationInteractionConfig.request();
      return requestConfig.requestSchemaAsDefinition();
    }

    log.info("initial-registration configuration not found, using default schema");
    return createDefaultRegistrationSchema();
  }

  /**
   * Create default registration schema.
   *
   * <p>Default schema includes basic OIDC standard claims for user registration:
   *
   * <ul>
   *   <li>email (required): User email address
   *   <li>password (required): User password (validated against tenant password policy)
   *   <li>name: Full name
   *   <li>given_name: First name
   *   <li>family_name: Last name
   *   <li>phone_number: Phone number
   * </ul>
   *
   * @return default registration schema
   */
  private static JsonSchemaDefinition createDefaultRegistrationSchema() {
    String defaultSchemaJson =
        """
        {
          "type": "object",
          "required": ["email", "password"],
          "properties": {
            "email": {
              "type": "string",
              "format": "email",
              "maxLength": 255
            },
            "password": {
              "type": "string"
            },
            "name": {
              "type": "string",
              "maxLength": 255
            },
            "given_name": {
              "type": "string",
              "maxLength": 255
            },
            "family_name": {
              "type": "string",
              "maxLength": 255
            },
            "phone_number": {
              "type": "string",
              "pattern": "^\\\\+?[0-9\\\\- ]{7,20}$"
            }
          }
        }
        """;
    return new JsonSchemaDefinition(JsonNodeWrapper.fromString(defaultSchemaJson));
  }
}

package org.idp.server.core.authentication.password;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.authentication.*;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.core.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.core.basic.json.schema.JsonSchemaValidator;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.*;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class PasswordRegistrationInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  PasswordEncodeDelegation passwordEncodeDelegation;
  JsonConverter jsonConverter;

  public PasswordRegistrationInteractor(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public AuthenticationInteractionResult interact(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      OAuthSession oAuthSession,
      UserRepository userRepository) {

    Map json = configurationQueryRepository.get(tenant, "signup", Map.class);
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

      return AuthenticationInteractionResult.clientError(
          response, type, DefaultSecurityEventType.user_signup_failure);
    }

    User existingUser =
        userRepository.findBy(tenant, request.optValueAsString("email", ""), "idp-server");

    if (existingUser.exists()) {

      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "user is conflict with username and password");

      return new AuthenticationInteractionResult(
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

    return new AuthenticationInteractionResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        user,
        authentication,
        response,
        DefaultSecurityEventType.user_signup);
  }
}

package org.idp.server.core.identity.verification.validation;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.basic.json.schema.JsonSchemaValidator;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationProcessConfiguration;

public class IdentityVerificationResponseValidator {
  IdentityVerificationProcessConfiguration processConfiguration;
  JsonNodeWrapper response;
  JsonConverter jsonConverter;

  public IdentityVerificationResponseValidator(
      IdentityVerificationProcessConfiguration processConfiguration, JsonNodeWrapper response) {
    this.processConfiguration = processConfiguration;
    this.response = response;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public IdentityVerificationValidationResult validate() {
    JsonSchemaDefinition jsonSchemaDefinition =
        processConfiguration.responseValidationSchemaAsDefinition();
    JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(jsonSchemaDefinition);

    JsonSchemaValidationResult validationResult = jsonSchemaValidator.validate(response);

    return new IdentityVerificationValidationResult(validationResult);
  }
}

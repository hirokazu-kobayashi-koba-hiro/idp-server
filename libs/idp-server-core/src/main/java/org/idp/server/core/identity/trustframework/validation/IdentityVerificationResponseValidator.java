package org.idp.server.core.identity.trustframework.validation;

import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.core.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.core.basic.json.schema.JsonSchemaValidator;
import org.idp.server.core.identity.trustframework.configuration.IdentityVerificationProcessConfiguration;

public class IdentityVerificationResponseValidator {
  IdentityVerificationProcessConfiguration processConfiguration;
  JsonNodeWrapper response;
  JsonConverter jsonConverter;

  public IdentityVerificationResponseValidator(
      IdentityVerificationProcessConfiguration processConfiguration, JsonNodeWrapper response) {
    this.processConfiguration = processConfiguration;
    this.response = response;
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  public IdentityVerificationValidationResult validate() {
    JsonNodeWrapper definition =
        jsonConverter.readTree(processConfiguration.responseValidationSchema());
    JsonSchemaDefinition jsonSchemaDefinition = new JsonSchemaDefinition(definition);
    JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(jsonSchemaDefinition);

    JsonSchemaValidationResult validationResult = jsonSchemaValidator.validate(response);

    return new IdentityVerificationValidationResult(validationResult);
  }
}

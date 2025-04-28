package org.idp.server.core.identity.verification.validation;

import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.core.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.core.basic.json.schema.JsonSchemaValidator;
import org.idp.server.core.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationProcessConfiguration;

public class IdentityVerificationRequestValidator {
  IdentityVerificationProcessConfiguration processConfiguration;
  IdentityVerificationRequest request;
  JsonConverter jsonConverter;

  public IdentityVerificationRequestValidator(
      IdentityVerificationProcessConfiguration processConfiguration,
      IdentityVerificationRequest request) {
    this.processConfiguration = processConfiguration;
    this.request = request;
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  public IdentityVerificationValidationResult validate() {
    JsonNodeWrapper definition =
        jsonConverter.readTree(processConfiguration.requestValidationSchema());
    JsonSchemaDefinition jsonSchemaDefinition = new JsonSchemaDefinition(definition);
    JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(jsonSchemaDefinition);

    JsonNodeWrapper requestJson = jsonConverter.readTree(request.toMap());
    JsonSchemaValidationResult validationResult = jsonSchemaValidator.validate(requestJson);

    return new IdentityVerificationValidationResult(validationResult);
  }
}

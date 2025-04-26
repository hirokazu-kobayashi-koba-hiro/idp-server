package org.idp.server.core.identity.trustframework.validation;

import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.core.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.core.basic.json.schema.JsonSchemaValidator;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationApplicationRequest;
import org.idp.server.core.identity.trustframework.configuration.IdentityVerificationProcessConfiguration;

public class IdentityVerificationApplicationValidator {
  IdentityVerificationProcessConfiguration processConfiguration;
  IdentityVerificationApplicationRequest request;
  JsonConverter jsonConverter;

  public IdentityVerificationApplicationValidator(
      IdentityVerificationProcessConfiguration processConfiguration,
      IdentityVerificationApplicationRequest request) {
    this.processConfiguration = processConfiguration;
    this.request = request;
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  public IdentityVerificationApplicationValidationResult validate() {
    JsonNodeWrapper definition =
        jsonConverter.readTree(processConfiguration.requestValidationSchema());
    JsonSchemaDefinition jsonSchemaDefinition = new JsonSchemaDefinition(definition);
    JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(jsonSchemaDefinition);

    JsonNodeWrapper requestJson = jsonConverter.readTree(request.toMap());
    JsonSchemaValidationResult validationResult = jsonSchemaValidator.validate(requestJson);

    return new IdentityVerificationApplicationValidationResult(validationResult);
  }
}

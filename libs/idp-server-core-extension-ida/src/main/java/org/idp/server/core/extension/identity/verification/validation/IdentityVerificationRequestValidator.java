/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.identity.verification.validation;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.basic.json.schema.JsonSchemaValidator;
import org.idp.server.core.extension.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;

public class IdentityVerificationRequestValidator {
  IdentityVerificationProcessConfiguration processConfiguration;
  IdentityVerificationRequest request;
  JsonConverter jsonConverter;

  public IdentityVerificationRequestValidator(
      IdentityVerificationProcessConfiguration processConfiguration,
      IdentityVerificationRequest request) {
    this.processConfiguration = processConfiguration;
    this.request = request;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public IdentityVerificationValidationResult validate() {

    JsonSchemaDefinition jsonSchemaDefinition =
        processConfiguration.requestValidationSchemaAsDefinition();
    JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(jsonSchemaDefinition);

    JsonNodeWrapper requestJson = jsonConverter.readTree(request.toMap());
    JsonSchemaValidationResult validationResult = jsonSchemaValidator.validate(requestJson);

    return new IdentityVerificationValidationResult(validationResult);
  }
}

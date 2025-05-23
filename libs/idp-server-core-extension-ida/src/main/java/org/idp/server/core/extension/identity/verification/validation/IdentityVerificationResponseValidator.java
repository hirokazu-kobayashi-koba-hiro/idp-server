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

package org.idp.server.core.extension.identity.verification.validation;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.basic.json.schema.JsonSchemaValidator;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;

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

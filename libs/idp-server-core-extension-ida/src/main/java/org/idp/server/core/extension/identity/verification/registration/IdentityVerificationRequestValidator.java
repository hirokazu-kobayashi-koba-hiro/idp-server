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

package org.idp.server.core.extension.identity.verification.registration;

import org.idp.server.core.extension.identity.verification.configuration.registration.IdentityVerificationRegistrationConfig;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationRequest;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.json.schema.JsonSchemaValidator;

public class IdentityVerificationRequestValidator {
  IdentityVerificationRegistrationConfig registrationConfiguration;
  IdentityVerificationRequest request;

  public IdentityVerificationRequestValidator(
      IdentityVerificationRegistrationConfig registrationConfiguration,
      IdentityVerificationRequest request) {
    this.registrationConfiguration = registrationConfiguration;
    this.request = request;
  }

  public IdentityVerificationValidationResult validate() {

    JsonSchemaDefinition jsonSchemaDefinition =
        registrationConfiguration.requestValidationSchemaAsDefinition();
    JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(jsonSchemaDefinition);

    JsonNodeWrapper requestJson = JsonNodeWrapper.fromMap(request.toMap());
    JsonSchemaValidationResult validationResult = jsonSchemaValidator.validate(requestJson);

    return new IdentityVerificationValidationResult(validationResult);
  }
}

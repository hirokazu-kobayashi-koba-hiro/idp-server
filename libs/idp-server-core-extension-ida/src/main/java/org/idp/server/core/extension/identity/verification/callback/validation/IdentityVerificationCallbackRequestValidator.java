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

package org.idp.server.core.extension.identity.verification.callback.validation;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.extension.identity.verification.configuration.common.IdentityVerificationBasicAuthConfig;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationRequest;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.json.schema.JsonSchemaValidator;
import org.idp.server.platform.type.RequestAttributes;

public class IdentityVerificationCallbackRequestValidator {
  IdentityVerificationProcessConfiguration processConfiguration;
  IdentityVerificationRequest request;
  RequestAttributes requestAttributes;

  public IdentityVerificationCallbackRequestValidator(
      IdentityVerificationProcessConfiguration processConfiguration,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {
    this.processConfiguration = processConfiguration;
    this.request = request;
    this.requestAttributes = requestAttributes;
  }

  public IdentityVerificationCallbackValidationResult validate() {

    if (processConfiguration.hasBasicAuth()) {
      BasicAuth basicAuth = requestAttributes.basicAuth();
      IdentityVerificationBasicAuthConfig basicAuthConfig = processConfiguration.basicAuthConfig();
      BasicAuth configurationBasicAuth = basicAuthConfig.basicAuth();

      if (!basicAuth.exists()) {

        List<String> errors = new ArrayList<>();
        errors.add("The identity verification request requires a basic authentication");
        return new IdentityVerificationCallbackValidationResult(false, errors);
      }

      if (!configurationBasicAuth.equals(basicAuth)) {

        List<String> errors = new ArrayList<>();
        errors.add("The identity verification request unmatch a basic authentication");
        return new IdentityVerificationCallbackValidationResult(false, errors);
      }
    }

    JsonSchemaDefinition jsonSchemaDefinition = processConfiguration.requestSchemaAsDefinition();
    JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(jsonSchemaDefinition);

    JsonNodeWrapper requestJson = JsonNodeWrapper.fromMap(request.toMap());
    JsonSchemaValidationResult validationResult = jsonSchemaValidator.validate(requestJson);

    return new IdentityVerificationCallbackValidationResult(
        validationResult.isValid(), validationResult.errors());
  }
}

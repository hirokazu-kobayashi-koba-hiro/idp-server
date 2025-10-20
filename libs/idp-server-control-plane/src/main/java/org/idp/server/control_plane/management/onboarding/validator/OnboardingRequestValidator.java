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

package org.idp.server.control_plane.management.onboarding.validator;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.control_plane.base.schema.ControlPlaneV1SchemaReader;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.onboarding.io.OnboardingRequest;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.json.schema.JsonSchemaValidator;

public class OnboardingRequestValidator {

  OnboardingRequest request;
  JsonSchemaValidator organizationSchemaValidator;
  JsonSchemaValidator tenantSchemaValidator;
  JsonSchemaValidator authorizationServerSchemaValidator;
  JsonSchemaValidator clientSchemaValidator;

  public OnboardingRequestValidator(OnboardingRequest request, boolean dryRun) {
    this.request = request;
    this.organizationSchemaValidator =
        new JsonSchemaValidator(ControlPlaneV1SchemaReader.organizationSchema());
    this.tenantSchemaValidator = new JsonSchemaValidator(ControlPlaneV1SchemaReader.tenantSchema());
    this.authorizationServerSchemaValidator =
        new JsonSchemaValidator(ControlPlaneV1SchemaReader.authorizationServerSchema());
    this.clientSchemaValidator = new JsonSchemaValidator(ControlPlaneV1SchemaReader.clientSchema());
  }

  public void validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(request.toMap());
    JsonSchemaValidationResult organizationResult =
        organizationSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("organization"));
    JsonSchemaValidationResult tenantResult =
        tenantSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("tenant"));
    JsonSchemaValidationResult authorizationServerResult =
        authorizationServerSchemaValidator.validate(
            jsonNodeWrapper.getValueAsJsonNode("authorization_server"));
    JsonSchemaValidationResult clientResult =
        clientSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("client"));

    throwExceptionIfInvalid(
        organizationResult, tenantResult, authorizationServerResult, clientResult);
  }

  void throwExceptionIfInvalid(
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      JsonSchemaValidationResult clientResult) {
    if (!organizationResult.isValid()
        || !tenantResult.isValid()
        || !authorizationServerResult.isValid()
        || !clientResult.isValid()) {
      List<String> errors = new ArrayList<>();
      errors.addAll(organizationResult.errors());
      errors.addAll(tenantResult.errors());
      errors.addAll(authorizationServerResult.errors());
      errors.addAll(clientResult.errors());
      throw new InvalidRequestException("onboarding validation is failed", errors);
    }
  }
}

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

import org.idp.server.control_plane.base.schema.ControlPlaneSchemaReader;
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
  boolean dryRun;

  public OnboardingRequestValidator(OnboardingRequest request, boolean dryRun) {
    this.request = request;
    this.organizationSchemaValidator =
        new JsonSchemaValidator(ControlPlaneSchemaReader.organizationSchema());
    this.tenantSchemaValidator = new JsonSchemaValidator(ControlPlaneSchemaReader.tenantSchema());
    this.authorizationServerSchemaValidator =
        new JsonSchemaValidator(ControlPlaneSchemaReader.authorizationServerSchema());
    this.clientSchemaValidator = new JsonSchemaValidator(ControlPlaneSchemaReader.clientSchema());
    this.dryRun = dryRun;
  }

  public OnboardingRequestValidationResult validate() {
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

    if (!organizationResult.isValid()
        || !tenantResult.isValid()
        || !authorizationServerResult.isValid()
        || !clientResult.isValid()) {
      return OnboardingRequestValidationResult.error(
          organizationResult, tenantResult, authorizationServerResult, clientResult, dryRun);
    }

    return OnboardingRequestValidationResult.success(
        organizationResult, tenantResult, authorizationServerResult, clientResult, dryRun);
  }
}

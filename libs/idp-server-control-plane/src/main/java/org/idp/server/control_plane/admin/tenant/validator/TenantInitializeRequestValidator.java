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

package org.idp.server.control_plane.admin.tenant.validator;

import org.idp.server.control_plane.admin.tenant.io.TenantInitializationRequest;
import org.idp.server.control_plane.base.schema.ControlPlaneSchemaReader;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.json.schema.JsonSchemaValidator;

public class TenantInitializeRequestValidator {

  TenantInitializationRequest request;
  JsonSchemaValidator organizationSchemaValidator;
  JsonSchemaValidator tenantSchemaValidator;
  JsonSchemaValidator authorizationServerSchemaValidator;
  JsonSchemaValidator adminUserSchemaValidator;
  JsonSchemaValidator clientSchemaValidator;
  boolean dryRun;

  public TenantInitializeRequestValidator(TenantInitializationRequest request, boolean dryRun) {
    this.request = request;
    this.organizationSchemaValidator =
        new JsonSchemaValidator(ControlPlaneSchemaReader.organizationSchema());
    this.tenantSchemaValidator = new JsonSchemaValidator(ControlPlaneSchemaReader.tenantSchema());
    this.authorizationServerSchemaValidator =
        new JsonSchemaValidator(ControlPlaneSchemaReader.authorizationServerSchema());
    this.adminUserSchemaValidator =
        new JsonSchemaValidator(ControlPlaneSchemaReader.adminUserSchema());
    this.clientSchemaValidator = new JsonSchemaValidator(ControlPlaneSchemaReader.clientSchema());
    this.dryRun = dryRun;
  }

  public TenantInitializeRequestValidationResult validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(request.toMap());
    JsonSchemaValidationResult organizationResult =
        organizationSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("organization"));
    JsonSchemaValidationResult tenantResult =
        tenantSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("tenant"));
    JsonSchemaValidationResult authorizationServerResult =
        authorizationServerSchemaValidator.validate(
            jsonNodeWrapper.getValueAsJsonNode("authorization_server"));
    JsonSchemaValidationResult adminUserResult =
        adminUserSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("user"));
    JsonSchemaValidationResult clientResult =
        clientSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("client"));

    if (!organizationResult.isValid()
        || !tenantResult.isValid()
        || !authorizationServerResult.isValid()
        || !adminUserResult.isValid()) {
      return TenantInitializeRequestValidationResult.error(
          organizationResult,
          tenantResult,
          authorizationServerResult,
          adminUserResult,
          clientResult,
          dryRun);
    }

    return TenantInitializeRequestValidationResult.success(
        organizationResult,
        tenantResult,
        authorizationServerResult,
        adminUserResult,
        clientResult,
        dryRun);
  }
}

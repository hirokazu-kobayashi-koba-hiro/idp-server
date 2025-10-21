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

package org.idp.server.control_plane.management.tenant.validator;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.control_plane.base.schema.ControlPlaneV1SchemaReader;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.tenant.io.TenantRequest;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.json.schema.JsonSchemaValidator;

public class TenantRequestValidator {

  TenantRequest request;
  JsonSchemaValidator tenantSchemaValidator;
  JsonSchemaValidator authorizationServerSchemaValidator;
  boolean dryRun;

  public TenantRequestValidator(TenantRequest request, boolean dryRun) {
    this.request = request;
    this.tenantSchemaValidator = new JsonSchemaValidator(ControlPlaneV1SchemaReader.tenantSchema());
    this.authorizationServerSchemaValidator =
        new JsonSchemaValidator(ControlPlaneV1SchemaReader.authorizationServerSchema());
    this.dryRun = dryRun;
  }

  public void validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(request.toMap());
    if (!jsonNodeWrapper.contains("tenant")) {
      List<String> errors = new ArrayList<>();
      errors.add("tenant is required.");
      throw new InvalidRequestException("Tenant request validation failed", errors);
    }
    if (!jsonNodeWrapper.contains("authorization_server")) {
      List<String> errors = new ArrayList<>();
      errors.add("authorization_server is required.");
      throw new InvalidRequestException("Tenant request validation failed", errors);
    }
    JsonSchemaValidationResult tenantResult =
        tenantSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("tenant"));
    JsonSchemaValidationResult authorizationServerResult =
        authorizationServerSchemaValidator.validate(
            jsonNodeWrapper.getValueAsJsonNode("authorization_server"));

    throwExceptionIfInvalid(tenantResult, authorizationServerResult);
  }

  void throwExceptionIfInvalid(
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult) {
    if (!tenantResult.isValid() || !authorizationServerResult.isValid()) {
      List<String> errorMessages = new ArrayList<>();
      if (!tenantResult.isValid()) {
        errorMessages.addAll(tenantResult.errors());
      }
      if (!authorizationServerResult.isValid()) {
        errorMessages.addAll(authorizationServerResult.errors());
      }
      throw new InvalidRequestException("Tenant request validation failed", errorMessages);
    }
  }
}

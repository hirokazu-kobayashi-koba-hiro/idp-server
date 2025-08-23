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

package org.idp.server.control_plane.admin.organization.validator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.admin.organization.io.OrganizationInitializationResponse;
import org.idp.server.control_plane.admin.organization.io.OrganizationInitializationStatus;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;

public class OrganizationInitializeRequestValidationResult {

  boolean isValid;
  JsonSchemaValidationResult organizationResult;
  JsonSchemaValidationResult tenantResult;
  JsonSchemaValidationResult authorizationServerResult;
  JsonSchemaValidationResult adminUserResult;
  JsonSchemaValidationResult clientResult;
  boolean dryRun;

  public static OrganizationInitializeRequestValidationResult success(
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      JsonSchemaValidationResult adminUserResult,
      JsonSchemaValidationResult clientResult,
      boolean dryRun) {
    return new OrganizationInitializeRequestValidationResult(
        true,
        organizationResult,
        tenantResult,
        authorizationServerResult,
        adminUserResult,
        clientResult,
        dryRun);
  }

  private OrganizationInitializeRequestValidationResult(
      boolean isValid,
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      JsonSchemaValidationResult adminUserResult,
      JsonSchemaValidationResult clientResult,
      boolean dryRun) {
    this.isValid = isValid;
    this.organizationResult = organizationResult;
    this.tenantResult = tenantResult;
    this.authorizationServerResult = authorizationServerResult;
    this.adminUserResult = adminUserResult;
    this.clientResult = clientResult;
    this.dryRun = dryRun;
  }

  public static OrganizationInitializeRequestValidationResult error(
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      JsonSchemaValidationResult adminUserResult,
      JsonSchemaValidationResult clientResult,
      boolean dryRun) {
    return new OrganizationInitializeRequestValidationResult(
        false,
        organizationResult,
        tenantResult,
        authorizationServerResult,
        adminUserResult,
        clientResult,
        dryRun);
  }

  public boolean isValid() {
    return isValid;
  }

  public OrganizationInitializationResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put(
        "error_description", "idp-server organization initialization validation is failed.");
    Map<String, Object> details = new HashMap<>();
    List<String> errors = new ArrayList<>();
    if (!organizationResult.isValid()) {
      errors.addAll(organizationResult.errors());
    }
    if (!tenantResult.isValid()) {
      errors.addAll(tenantResult.errors());
    }
    if (!authorizationServerResult.isValid()) {
      errors.addAll(authorizationServerResult.errors());
    }
    if (!adminUserResult.isValid()) {
      errors.addAll(adminUserResult.errors());
    }
    if (!clientResult.isValid()) {
      errors.addAll(clientResult.errors());
    }
    details.put("messages", errors);
    response.put("error_details", details);
    return new OrganizationInitializationResponse(
        OrganizationInitializationStatus.INVALID_REQUEST, response);
  }
}

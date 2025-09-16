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

package org.idp.server.control_plane.management.oidc.authorization.validator;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementResponse;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementStatus;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;

public class AuthorizationServerRequestValidationResult {

  boolean isValid;
  JsonSchemaValidationResult validationResult;
  boolean dryRun;

  public static AuthorizationServerRequestValidationResult success(
      JsonSchemaValidationResult clientResult, boolean dryRun) {
    return new AuthorizationServerRequestValidationResult(true, clientResult, dryRun);
  }

  public static AuthorizationServerRequestValidationResult error(
      JsonSchemaValidationResult clientResul, boolean dryRun) {
    return new AuthorizationServerRequestValidationResult(false, clientResul, dryRun);
  }

  private AuthorizationServerRequestValidationResult(
      boolean isValid, JsonSchemaValidationResult validationResult, boolean dryRun) {
    this.isValid = isValid;
    this.validationResult = validationResult;
    this.dryRun = dryRun;
  }

  public boolean isValid() {
    return isValid;
  }

  public AuthorizationServerManagementResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "Invalid request");
    response.put("error_messages", validationResult.errors());
    return new AuthorizationServerManagementResponse(
        AuthorizationServerManagementStatus.INVALID_REQUEST, response);
  }
}

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

package org.idp.server.control_plane.management.role.validator;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.role.io.RoleManagementResponse;
import org.idp.server.control_plane.management.role.io.RoleManagementStatus;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;

public class RoleRequestValidationResult {

  boolean isValid;
  JsonSchemaValidationResult validationResult;
  boolean dryRun;

  public static RoleRequestValidationResult success(
      JsonSchemaValidationResult adminUserResult, boolean dryRun) {
    return new RoleRequestValidationResult(true, adminUserResult, dryRun);
  }

  private RoleRequestValidationResult(
      boolean isValid, JsonSchemaValidationResult validationResult, boolean dryRun) {
    this.isValid = isValid;
    this.validationResult = validationResult;
    this.dryRun = dryRun;
  }

  public static RoleRequestValidationResult error(
      JsonSchemaValidationResult adminUserResult, boolean dryRun) {
    return new RoleRequestValidationResult(false, adminUserResult, dryRun);
  }

  public boolean isValid() {
    return isValid;
  }

  public RoleManagementResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "role registration validation is failed");
    Map<String, Object> details = new HashMap<>();
    if (!validationResult.isValid()) {
      {
        details.put("massages", validationResult.errors());
      }
    }
    response.put("error_details", details);
    return new RoleManagementResponse(RoleManagementStatus.INVALID_REQUEST, response);
  }
}

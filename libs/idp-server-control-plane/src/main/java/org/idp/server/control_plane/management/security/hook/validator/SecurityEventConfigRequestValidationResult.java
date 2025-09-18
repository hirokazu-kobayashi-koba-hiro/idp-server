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

package org.idp.server.control_plane.management.security.hook.validator;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementStatus;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;

public class SecurityEventConfigRequestValidationResult {

  boolean isValid;
  JsonSchemaValidationResult validationResult;
  boolean dryRun;

  public static SecurityEventConfigRequestValidationResult success(
      JsonSchemaValidationResult adminUserResult, boolean dryRun) {
    return new SecurityEventConfigRequestValidationResult(true, adminUserResult, dryRun);
  }

  private SecurityEventConfigRequestValidationResult(
      boolean isValid, JsonSchemaValidationResult validationResult, boolean dryRun) {
    this.isValid = isValid;
    this.validationResult = validationResult;
    this.dryRun = dryRun;
  }

  public static SecurityEventConfigRequestValidationResult error(
      JsonSchemaValidationResult adminUserResult, boolean dryRun) {
    return new SecurityEventConfigRequestValidationResult(false, adminUserResult, dryRun);
  }

  public boolean isValid() {
    return isValid;
  }

  public SecurityEventHookConfigManagementResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "security enve hook registration validation is failed");
    response.put("error_messages", validationResult.errors());
    return new SecurityEventHookConfigManagementResponse(
        SecurityEventHookConfigManagementStatus.INVALID_REQUEST, response);
  }
}

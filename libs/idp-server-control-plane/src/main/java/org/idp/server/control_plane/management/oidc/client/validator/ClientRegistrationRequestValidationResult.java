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

package org.idp.server.control_plane.management.oidc.client.validator;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementStatus;

public class ClientRegistrationRequestValidationResult {

  boolean isValid;
  JsonSchemaValidationResult clientResult;
  boolean dryRun;

  public static ClientRegistrationRequestValidationResult success(
      JsonSchemaValidationResult clientResult, boolean dryRun) {
    return new ClientRegistrationRequestValidationResult(true, clientResult, dryRun);
  }

  public static ClientRegistrationRequestValidationResult error(
      JsonSchemaValidationResult clientResul, boolean dryRun) {
    return new ClientRegistrationRequestValidationResult(false, clientResul, dryRun);
  }

  private ClientRegistrationRequestValidationResult(
      boolean isValid, JsonSchemaValidationResult clientResult, boolean dryRun) {
    this.isValid = isValid;
    this.clientResult = clientResult;
    this.dryRun = dryRun;
  }

  public boolean isValid() {
    return isValid;
  }

  public ClientManagementResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "Invalid request");
    Map<String, Object> details = new HashMap<>();
    if (!clientResult.isValid()) {
      details.put("client", clientResult.errors());
    }
    response.put("details", details);
    return new ClientManagementResponse(ClientManagementStatus.INVALID_REQUEST, response);
  }
}

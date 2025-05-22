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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.control_plane.management.onboarding.io.OnboardingResponse;
import org.idp.server.control_plane.management.onboarding.io.OnboardingStatus;

public class OnboardingRequestValidationResult {

  boolean isValid;
  JsonSchemaValidationResult organizationResult;
  JsonSchemaValidationResult tenantResult;
  JsonSchemaValidationResult authorizationServerResult;
  JsonSchemaValidationResult clientResult;
  boolean dryRun;

  public static OnboardingRequestValidationResult success(
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      JsonSchemaValidationResult clientResult,
      boolean dryRun) {
    return new OnboardingRequestValidationResult(
        true, organizationResult, tenantResult, authorizationServerResult, clientResult, dryRun);
  }

  public static OnboardingRequestValidationResult error(
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      JsonSchemaValidationResult clientResul,
      boolean dryRun) {
    return new OnboardingRequestValidationResult(
        false, organizationResult, tenantResult, authorizationServerResult, clientResul, dryRun);
  }

  private OnboardingRequestValidationResult(
      boolean isValid,
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      JsonSchemaValidationResult clientResult,
      boolean dryRun) {
    this.isValid = isValid;
    this.organizationResult = organizationResult;
    this.tenantResult = tenantResult;
    this.authorizationServerResult = authorizationServerResult;
    this.clientResult = clientResult;
    this.dryRun = dryRun;
  }

  public boolean isValid() {
    return isValid;
  }

  public OnboardingResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "Invalid request");
    Map<String, Object> details = new HashMap<>();
    if (!organizationResult.isValid()) {
      details.put("organization", organizationResult.errors());
    }
    if (!tenantResult.isValid()) {
      details.put("tenant", tenantResult.errors());
    }
    if (!authorizationServerResult.isValid()) {
      details.put("authorization_server", authorizationServerResult.errors());
    }
    if (!clientResult.isValid()) {
      details.put("client", clientResult.errors());
    }

    response.put("details", details);
    return new OnboardingResponse(OnboardingStatus.INVALID_REQUEST, response);
  }
}

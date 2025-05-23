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

package org.idp.server.control_plane.management.tenant.invitation.validator;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.control_plane.management.tenant.invitation.io.TenantInvitationManagementResponse;
import org.idp.server.control_plane.management.tenant.invitation.io.TenantInvitationManagementStatus;

public class OrganizationInvitationRequestValidationResult {

  boolean isValid;
  JsonSchemaValidationResult tenantInvitationResult;
  boolean dryRun;

  public static OrganizationInvitationRequestValidationResult success(
      JsonSchemaValidationResult organizationResult, boolean dryRun) {
    return new OrganizationInvitationRequestValidationResult(true, organizationResult, dryRun);
  }

  public static OrganizationInvitationRequestValidationResult error(
      JsonSchemaValidationResult organizationResult, boolean dryRun) {
    return new OrganizationInvitationRequestValidationResult(false, organizationResult, dryRun);
  }

  private OrganizationInvitationRequestValidationResult(
      boolean isValid, JsonSchemaValidationResult tenantInvitationResult, boolean dryRun) {
    this.isValid = isValid;
    this.tenantInvitationResult = tenantInvitationResult;
    this.dryRun = dryRun;
  }

  public boolean isValid() {
    return isValid;
  }

  public TenantInvitationManagementResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "Invalid request");
    Map<String, Object> details = new HashMap<>();
    if (!tenantInvitationResult.isValid()) {
      details.put("tenant_invitation", tenantInvitationResult.errors());
    }

    response.put("details", details);
    return new TenantInvitationManagementResponse(
        TenantInvitationManagementStatus.INVALID_REQUEST, response);
  }
}

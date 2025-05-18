package org.idp.server.control_plane.management.organization.invitation.validator;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.control_plane.management.organization.invitation.io.TenantInvitationManagementResponse;
import org.idp.server.control_plane.management.organization.invitation.io.TenantInvitationManagementStatus;

public class OrganizationInvitationRequestValidationResult {

  boolean isValid;
  JsonSchemaValidationResult organizationResult;
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
      boolean isValid, JsonSchemaValidationResult organizationResult, boolean dryRun) {
    this.isValid = isValid;
    this.organizationResult = organizationResult;
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
    if (!organizationResult.isValid()) {
      details.put("organization", organizationResult.errors());
    }

    response.put("details", details);
    return new TenantInvitationManagementResponse(
        TenantInvitationManagementStatus.INVALID_REQUEST, response);
  }
}

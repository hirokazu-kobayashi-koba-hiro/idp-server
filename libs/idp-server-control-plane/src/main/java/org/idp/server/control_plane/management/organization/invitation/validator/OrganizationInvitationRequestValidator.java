package org.idp.server.control_plane.management.organization.invitation.validator;

import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.basic.json.schema.JsonSchemaValidator;
import org.idp.server.control_plane.base.schema.SchemaReader;
import org.idp.server.control_plane.management.organization.invitation.io.TenantInvitationManagementRequest;

public class OrganizationInvitationRequestValidator {

  TenantInvitationManagementRequest request;
  JsonSchemaValidator organizationInvitationSchemaValidator;
  boolean dryRun;

  public OrganizationInvitationRequestValidator(
      TenantInvitationManagementRequest request, boolean dryRun) {
    this.request = request;
    this.organizationInvitationSchemaValidator =
        new JsonSchemaValidator(SchemaReader.organizationInvitationSchema());
    this.dryRun = dryRun;
  }

  public OrganizationInvitationRequestValidationResult validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(request.toMap());
    JsonSchemaValidationResult organizationResult =
        organizationInvitationSchemaValidator.validate(jsonNodeWrapper);

    if (!organizationResult.isValid()) {
      return OrganizationInvitationRequestValidationResult.error(organizationResult, dryRun);
    }

    return OrganizationInvitationRequestValidationResult.success(organizationResult, dryRun);
  }
}

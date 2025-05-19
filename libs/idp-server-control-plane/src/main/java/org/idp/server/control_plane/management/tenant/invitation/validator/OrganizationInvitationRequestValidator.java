package org.idp.server.control_plane.management.tenant.invitation.validator;

import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.basic.json.schema.JsonSchemaValidator;
import org.idp.server.control_plane.base.schema.SchemaReader;
import org.idp.server.control_plane.management.tenant.invitation.io.TenantInvitationManagementRequest;

public class OrganizationInvitationRequestValidator {

  TenantInvitationManagementRequest request;
  JsonSchemaValidator tenantInvitationSchemaValidator;
  boolean dryRun;

  public OrganizationInvitationRequestValidator(
      TenantInvitationManagementRequest request, boolean dryRun) {
    this.request = request;
    this.tenantInvitationSchemaValidator =
        new JsonSchemaValidator(SchemaReader.tenantInvitationSchema());
    this.dryRun = dryRun;
  }

  public OrganizationInvitationRequestValidationResult validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(request.toMap());
    JsonSchemaValidationResult tenantInvitationResult =
        tenantInvitationSchemaValidator.validate(jsonNodeWrapper);

    if (!tenantInvitationResult.isValid()) {
      return OrganizationInvitationRequestValidationResult.error(tenantInvitationResult, dryRun);
    }

    return OrganizationInvitationRequestValidationResult.success(tenantInvitationResult, dryRun);
  }
}

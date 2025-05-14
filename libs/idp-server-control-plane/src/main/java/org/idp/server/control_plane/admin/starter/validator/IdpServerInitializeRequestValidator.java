package org.idp.server.control_plane.admin.starter.validator;

import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.basic.json.schema.JsonSchemaValidator;
import org.idp.server.control_plane.admin.starter.io.IdpServerStarterRequest;
import org.idp.server.control_plane.base.schema.SchemaReader;

public class IdpServerInitializeRequestValidator {

  IdpServerStarterRequest request;
  JsonSchemaValidator organizationSchemaValidator;
  JsonSchemaValidator tenantSchemaValidator;
  JsonSchemaValidator authorizationServerSchemaValidator;
  JsonSchemaValidator adminUserSchemaValidator;
  JsonSchemaValidator clientSchemaValidator;
  boolean dryRun;

  public IdpServerInitializeRequestValidator(IdpServerStarterRequest request, boolean dryRun) {
    this.request = request;
    this.organizationSchemaValidator = new JsonSchemaValidator(SchemaReader.organizationSchema());
    this.tenantSchemaValidator = new JsonSchemaValidator(SchemaReader.tenantSchema());
    this.authorizationServerSchemaValidator =
        new JsonSchemaValidator(SchemaReader.authorizationServerSchema());
    this.adminUserSchemaValidator = new JsonSchemaValidator(SchemaReader.adminUserSchema());
    this.clientSchemaValidator = new JsonSchemaValidator(SchemaReader.clientSchema());
    this.dryRun = dryRun;
  }

  public IdpServerInitializeRequestValidationResult validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(request.toMap());
    JsonSchemaValidationResult organizationResult =
        organizationSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("organization"));
    JsonSchemaValidationResult tenantResult =
        tenantSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("tenant"));
    JsonSchemaValidationResult authorizationServerResult =
        authorizationServerSchemaValidator.validate(
            jsonNodeWrapper.getValueAsJsonNode("authorization_server"));
    JsonSchemaValidationResult adminUserResult =
        adminUserSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("user"));
    JsonSchemaValidationResult clientResult =
        clientSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("client"));

    if (!organizationResult.isValid()
        || !tenantResult.isValid()
        || !authorizationServerResult.isValid()
        || !adminUserResult.isValid()) {
      return IdpServerInitializeRequestValidationResult.error(
          organizationResult,
          tenantResult,
          authorizationServerResult,
          adminUserResult,
          clientResult,
          dryRun);
    }

    return IdpServerInitializeRequestValidationResult.success(
        organizationResult,
        tenantResult,
        authorizationServerResult,
        adminUserResult,
        clientResult,
        dryRun);
  }
}

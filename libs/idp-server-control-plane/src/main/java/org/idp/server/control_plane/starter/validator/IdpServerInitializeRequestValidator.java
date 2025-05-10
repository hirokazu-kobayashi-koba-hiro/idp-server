package org.idp.server.control_plane.starter.validator;

import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.basic.json.schema.JsonSchemaValidator;
import org.idp.server.control_plane.schema.SchemaReader;
import org.idp.server.control_plane.starter.io.IdpServerStarterRequest;

public class IdpServerInitializeRequestValidator {

  IdpServerStarterRequest request;
  JsonSchemaValidator organizationSchemaValidator;
  JsonSchemaValidator tenantSchemaValidator;
  JsonSchemaValidator authorizationServerSchemaValidator;
  JsonSchemaValidator adminUserSchemaValidator;

  public IdpServerInitializeRequestValidator(IdpServerStarterRequest request) {
    this.request = request;
    this.organizationSchemaValidator = new JsonSchemaValidator(SchemaReader.organizationSchema());
    this.tenantSchemaValidator = new JsonSchemaValidator(SchemaReader.tenantSchema());
    this.authorizationServerSchemaValidator =
        new JsonSchemaValidator(SchemaReader.authorizationServerSchema());
    this.adminUserSchemaValidator = new JsonSchemaValidator(SchemaReader.adminUserSchema());
  }

  public IdpServerInitializeRequestValidationResult validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(request.toMap());
    JsonSchemaValidationResult organizationResult =
        organizationSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("organization"));
    JsonSchemaValidationResult tenantResult =
        tenantSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("tenant"));
    JsonSchemaValidationResult authorizationServerResult =
        authorizationServerSchemaValidator.validate(
            jsonNodeWrapper.getValueAsJsonNode("authorization_server_configuration"));
    JsonSchemaValidationResult adminUserResult =
        adminUserSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("user"));

    if (!organizationResult.isValid()
        || !tenantResult.isValid()
        || !authorizationServerResult.isValid()
        || !adminUserResult.isValid()) {
      return IdpServerInitializeRequestValidationResult.error(
          organizationResult, tenantResult, authorizationServerResult, adminUserResult);
    }

    return IdpServerInitializeRequestValidationResult.success(
        organizationResult, tenantResult, authorizationServerResult, adminUserResult);
  }
}

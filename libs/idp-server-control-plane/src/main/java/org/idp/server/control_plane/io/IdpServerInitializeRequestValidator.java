package org.idp.server.control_plane.io;

import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.basic.json.schema.JsonSchemaValidator;
import org.idp.server.control_plane.schema.AuthorizationServerSchemaDefinition;
import org.idp.server.control_plane.schema.TenantSchemaDefinition;

public class IdpServerInitializeRequestValidator {

  Map<String, Object> request;
  JsonSchemaValidator tenantSchemaValidator;
  JsonSchemaValidator authorizationServerSchemaValidator;

  public IdpServerInitializeRequestValidator(Map<String, Object> request) {
    this.request = request;
    this.tenantSchemaValidator = new JsonSchemaValidator(new TenantSchemaDefinition().definition());
    this.authorizationServerSchemaValidator =
        new JsonSchemaValidator(new AuthorizationServerSchemaDefinition().definition());
  }

  public IdpServerInitializeRequestValidationResult validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(request);
    JsonSchemaValidationResult organizationResult =
        tenantSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("organization"));
    JsonSchemaValidationResult tenantResult =
        tenantSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("tenant"));
    JsonSchemaValidationResult authorizationServerResult =
        authorizationServerSchemaValidator.validate(
            jsonNodeWrapper.getValueAsJsonNode("authorization_server_configuration"));

    if (!organizationResult.isValid()
        || !tenantResult.isValid()
        || !authorizationServerResult.isValid()) {
      return IdpServerInitializeRequestValidationResult.error(
          organizationResult, tenantResult, authorizationServerResult);
    }

    return IdpServerInitializeRequestValidationResult.success(
        organizationResult, tenantResult, authorizationServerResult);
  }
}

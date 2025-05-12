package org.idp.server.control_plane.management.oidc.client.validator;

import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.basic.json.schema.JsonSchemaValidator;
import org.idp.server.control_plane.base.schema.SchemaReader;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;

public class ClientRegistrationRequestValidator {

  ClientRegistrationRequest request;
  JsonSchemaValidator clientSchemaValidator;

  public ClientRegistrationRequestValidator(ClientRegistrationRequest request) {
    this.request = request;
    this.clientSchemaValidator = new JsonSchemaValidator(SchemaReader.clientSchema());
  }

  public ClientRegistrationRequestValidationResult validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(request.toMap());
    JsonSchemaValidationResult clientResult =
        clientSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("client"));

    if (!clientResult.isValid()) {
      return ClientRegistrationRequestValidationResult.error(clientResult, request.isDryRun());
    }

    return ClientRegistrationRequestValidationResult.success(clientResult, request.isDryRun());
  }
}

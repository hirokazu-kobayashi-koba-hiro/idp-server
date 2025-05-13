package org.idp.server.control_plane.management.oidc.client.validator;

import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.basic.json.schema.JsonSchemaValidator;
import org.idp.server.control_plane.base.schema.SchemaReader;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;

public class ClientRegistrationRequestValidator {

  ClientRegistrationRequest request;
  boolean dryRun;
  JsonSchemaValidator clientSchemaValidator;

  public ClientRegistrationRequestValidator(ClientRegistrationRequest request, boolean dryRun) {
    this.request = request;
    this.dryRun = dryRun;
    this.clientSchemaValidator = new JsonSchemaValidator(SchemaReader.clientSchema());
  }

  public ClientRegistrationRequestValidationResult validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(request.toMap());
    JsonSchemaValidationResult clientResult =
        clientSchemaValidator.validate(jsonNodeWrapper);

    if (!clientResult.isValid()) {
      return ClientRegistrationRequestValidationResult.error(clientResult, dryRun);
    }

    return ClientRegistrationRequestValidationResult.success(clientResult, dryRun);
  }
}

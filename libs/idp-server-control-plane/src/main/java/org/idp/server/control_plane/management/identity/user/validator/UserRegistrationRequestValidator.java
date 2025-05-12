package org.idp.server.control_plane.management.identity.user.validator;

import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.basic.json.schema.JsonSchemaValidator;
import org.idp.server.control_plane.base.schema.SchemaReader;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;

public class UserRegistrationRequestValidator {

  UserRegistrationRequest request;
  JsonSchemaValidator userSchemaValidator;

  public UserRegistrationRequestValidator(UserRegistrationRequest request) {
    this.request = request;
    this.userSchemaValidator = new JsonSchemaValidator(SchemaReader.adminUserSchema());
  }

  public UserRegistrationRequestValidationResult validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(request.toMap());
    JsonSchemaValidationResult userResult =
        userSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("user"));

    if (!userResult.isValid()) {
      return UserRegistrationRequestValidationResult.error(userResult, request.isDryRun());
    }

    return UserRegistrationRequestValidationResult.success(userResult, request.isDryRun());
  }
}

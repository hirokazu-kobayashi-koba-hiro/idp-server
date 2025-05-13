package org.idp.server.control_plane.management.identity.user.validator;

import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.basic.json.schema.JsonSchemaValidator;
import org.idp.server.control_plane.base.schema.SchemaReader;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;

public class UserRegistrationRequestValidator {

  UserRegistrationRequest request;
  boolean dryRun;
  JsonSchemaValidator userSchemaValidator;

  public UserRegistrationRequestValidator(UserRegistrationRequest request, boolean dryRun) {
    this.request = request;
    this.dryRun = dryRun;
    this.userSchemaValidator = new JsonSchemaValidator(SchemaReader.adminUserSchema());
  }

  public UserRegistrationRequestValidationResult validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(request.toMap());
    JsonSchemaValidationResult userResult =
        userSchemaValidator.validate(jsonNodeWrapper);

    if (!userResult.isValid()) {
      return UserRegistrationRequestValidationResult.error(userResult, dryRun);
    }

    return UserRegistrationRequestValidationResult.success(userResult, dryRun);
  }
}

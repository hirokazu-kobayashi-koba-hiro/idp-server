package org.idp.server.control_plane.onboarding.validator;

import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.basic.json.schema.JsonSchemaValidator;
import org.idp.server.control_plane.onboarding.io.OnboardingRequest;
import org.idp.server.control_plane.schema.SchemaReader;

public class OnboardingRequestValidator {

  OnboardingRequest request;
  JsonSchemaValidator organizationSchemaValidator;
  JsonSchemaValidator tenantSchemaValidator;
  JsonSchemaValidator authorizationServerSchemaValidator;
  JsonSchemaValidator clientSchemaValidator;

  public OnboardingRequestValidator(OnboardingRequest request) {
    this.request = request;
    this.organizationSchemaValidator = new JsonSchemaValidator(SchemaReader.organizationSchema());
    this.tenantSchemaValidator = new JsonSchemaValidator(SchemaReader.tenantSchema());
    this.authorizationServerSchemaValidator =
        new JsonSchemaValidator(SchemaReader.authorizationServerSchema());
    this.clientSchemaValidator = new JsonSchemaValidator(SchemaReader.clientSchema());
  }

  public OnboardingRequestValidationResult validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(request.toMap());
    JsonSchemaValidationResult organizationResult =
        organizationSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("organization"));
    JsonSchemaValidationResult tenantResult =
        tenantSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("tenant"));
    JsonSchemaValidationResult authorizationServerResult =
        authorizationServerSchemaValidator.validate(
            jsonNodeWrapper.getValueAsJsonNode("authorization_server"));
    JsonSchemaValidationResult clientResult =
        clientSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("client"));

    if (!organizationResult.isValid()
        || !tenantResult.isValid()
        || !authorizationServerResult.isValid()
        || !clientResult.isValid()) {
      return OnboardingRequestValidationResult.error(
          organizationResult,
          tenantResult,
          authorizationServerResult,
          clientResult,
          request.isDryRun());
    }

    return OnboardingRequestValidationResult.success(
        organizationResult,
        tenantResult,
        authorizationServerResult,
        clientResult,
        request.isDryRun());
  }
}

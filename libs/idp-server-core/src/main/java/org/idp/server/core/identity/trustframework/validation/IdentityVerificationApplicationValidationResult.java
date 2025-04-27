package org.idp.server.core.identity.trustframework.validation;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationResponse;

public class IdentityVerificationApplicationValidationResult {

  JsonSchemaValidationResult validationResult;

  public IdentityVerificationApplicationValidationResult(
      JsonSchemaValidationResult validationResult) {
    this.validationResult = validationResult;
  }

  public boolean isOK() {
    return validationResult.isValid();
  }

  public boolean isError() {
    return !validationResult.isValid();
  }

  public IdentityVerificationResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("error", "invalid_request");
    response.put("error_description", "identity verification application is invalid.");
    response.put("error_details", validationResult.errors());
    return IdentityVerificationResponse.CLIENT_ERROR(response);
  }
}

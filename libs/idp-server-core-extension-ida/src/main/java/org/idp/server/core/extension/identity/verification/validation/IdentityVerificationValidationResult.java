/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.identity.verification.validation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationResponse;

public class IdentityVerificationValidationResult {

  JsonSchemaValidationResult validationResult;

  public IdentityVerificationValidationResult() {
    this.validationResult = JsonSchemaValidationResult.empty();
  }

  public IdentityVerificationValidationResult(JsonSchemaValidationResult validationResult) {
    this.validationResult = validationResult;
  }

  public boolean isOK() {
    return validationResult.isValid();
  }

  public boolean isError() {
    return !validationResult.isValid();
  }

  public List<String> errors() {
    return validationResult.errors();
  }

  public IdentityVerificationResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("error", "invalid_request");
    response.put("error_description", "identity verification application is invalid.");
    response.put("error_details", validationResult.errors());
    return IdentityVerificationResponse.CLIENT_ERROR(response);
  }
}

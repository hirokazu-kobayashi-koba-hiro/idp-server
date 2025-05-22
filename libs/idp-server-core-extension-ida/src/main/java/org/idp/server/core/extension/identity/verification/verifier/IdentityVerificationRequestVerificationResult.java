/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.identity.verification.verifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationResponse;

public class IdentityVerificationRequestVerificationResult {

  boolean valid;
  List<String> errors;

  private IdentityVerificationRequestVerificationResult(boolean valid, List<String> errors) {
    this.valid = valid;
    this.errors = errors;
  }

  public static IdentityVerificationRequestVerificationResult empty() {
    return new IdentityVerificationRequestVerificationResult(false, List.of());
  }

  public static IdentityVerificationRequestVerificationResult success() {
    return new IdentityVerificationRequestVerificationResult(true, List.of());
  }

  public static IdentityVerificationRequestVerificationResult failure(List<String> errors) {
    return new IdentityVerificationRequestVerificationResult(false, errors);
  }

  public boolean isValid() {
    return valid;
  }

  public boolean isError() {
    return !valid;
  }

  public List<String> errors() {
    return errors;
  }

  public IdentityVerificationResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("error", "invalid_request");
    response.put("error_description", "identity verification application is invalid.");
    response.put("error_details", errors);
    return IdentityVerificationResponse.CLIENT_ERROR(response);
  }
}

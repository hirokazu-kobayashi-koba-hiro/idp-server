/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.extension.identity.verification.application.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationApplicationResponse;

public class IdentityVerificationApplicationValidationResult {

  boolean valid;
  List<String> errors;
  ;

  public IdentityVerificationApplicationValidationResult() {
    this.valid = false;
    this.errors = new ArrayList<>();
  }

  public IdentityVerificationApplicationValidationResult(boolean valid, List<String> errors) {
    this.valid = valid;
    this.errors = errors;
  }

  public boolean isOK() {
    return valid;
  }

  public boolean isError() {
    return !valid;
  }

  public List<String> errors() {
    return errors;
  }

  public IdentityVerificationApplicationResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("error", "invalid_request");
    response.put(
        "error_description",
        "The identity verification request is invalid. Please review your input for missing or incorrect fields.");
    response.put("error_messages", errors);

    return IdentityVerificationApplicationResponse.CLIENT_ERROR(response);
  }
}

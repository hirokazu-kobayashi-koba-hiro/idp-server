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

package org.idp.server.core.extension.identity.verification.callback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationApplicationResponse;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;

public class IdentityVerificationCallbackValidationResult {

  JsonSchemaValidationResult validationResult;

  public IdentityVerificationCallbackValidationResult() {
    this.validationResult = JsonSchemaValidationResult.empty();
  }

  public IdentityVerificationCallbackValidationResult(JsonSchemaValidationResult validationResult) {
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

  public IdentityVerificationApplicationResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("error", "invalid_request");
    response.put("error_description", "identity verification is invalid.");
    response.put("error_details", validationResult.errors());
    return IdentityVerificationApplicationResponse.CLIENT_ERROR(response);
  }
}

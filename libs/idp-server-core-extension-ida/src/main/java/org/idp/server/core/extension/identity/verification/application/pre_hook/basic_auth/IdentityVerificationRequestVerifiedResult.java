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

package org.idp.server.core.extension.identity.verification.application.pre_hook.basic_auth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationResponse;

public class IdentityVerificationRequestVerifiedResult {

  boolean valid;
  List<String> errors;

  private IdentityVerificationRequestVerifiedResult(boolean valid, List<String> errors) {
    this.valid = valid;
    this.errors = errors;
  }

  public static IdentityVerificationRequestVerifiedResult empty() {
    return new IdentityVerificationRequestVerifiedResult(false, List.of());
  }

  public static IdentityVerificationRequestVerifiedResult success() {
    return new IdentityVerificationRequestVerifiedResult(true, List.of());
  }

  public static IdentityVerificationRequestVerifiedResult failure(List<String> errors) {
    return new IdentityVerificationRequestVerifiedResult(false, errors);
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
    response.put(
        "error_description",
        "The identity verification request could not be completed due to a business rule violation.");
    response.put("error_messages", errors);
    return IdentityVerificationResponse.CLIENT_ERROR(response);
  }
}

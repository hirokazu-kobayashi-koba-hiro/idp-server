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

package org.idp.server.control_plane.management.identity.user.verifier;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;

public class UserRegistrationVerificationResult {
  boolean isValid;
  VerificationResult userVerificationResult;
  boolean dryRun;

  public static UserRegistrationVerificationResult success(
      VerificationResult userVerificationResult, boolean dryRun) {
    return new UserRegistrationVerificationResult(true, userVerificationResult, dryRun);
  }

  public static UserRegistrationVerificationResult error(
      VerificationResult userVerificationResult, boolean dryRun) {
    return new UserRegistrationVerificationResult(false, userVerificationResult, dryRun);
  }

  private UserRegistrationVerificationResult(
      boolean isValid, VerificationResult userVerificationResult, boolean dryRun) {
    this.isValid = isValid;
    this.userVerificationResult = userVerificationResult;
    this.dryRun = dryRun;
  }

  public boolean isValid() {
    return isValid;
  }

  public UserManagementResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "user registration verification is failed");
    Map<String, Object> details = new HashMap<>();
    if (!userVerificationResult.isValid()) {
      details.put("user", userVerificationResult.errors());
    }
    response.put("details", details);
    return new UserManagementResponse(UserManagementStatus.INVALID_REQUEST, response);
  }
}

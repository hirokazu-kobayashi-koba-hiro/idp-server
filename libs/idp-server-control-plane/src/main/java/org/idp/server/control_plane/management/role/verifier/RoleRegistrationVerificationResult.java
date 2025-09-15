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

package org.idp.server.control_plane.management.role.verifier;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.control_plane.management.role.io.RoleManagementResponse;
import org.idp.server.control_plane.management.role.io.RoleManagementStatus;

public class RoleRegistrationVerificationResult {
  boolean isValid;
  VerificationResult verificationResult;
  boolean dryRun;

  public static RoleRegistrationVerificationResult success(
      VerificationResult verificationResult, boolean dryRun) {
    return new RoleRegistrationVerificationResult(true, verificationResult, dryRun);
  }

  public static RoleRegistrationVerificationResult error(
      VerificationResult verificationResult, boolean dryRun) {
    return new RoleRegistrationVerificationResult(false, verificationResult, dryRun);
  }

  private RoleRegistrationVerificationResult(
      boolean isValid, VerificationResult verificationResult, boolean dryRun) {
    this.isValid = isValid;
    this.verificationResult = verificationResult;
    this.dryRun = dryRun;
  }

  public boolean isValid() {
    return isValid;
  }

  public RoleManagementResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "role registration verification is failed");
    response.put("error_messages", verificationResult.errors());
    return new RoleManagementResponse(RoleManagementStatus.INVALID_REQUEST, response);
  }
}

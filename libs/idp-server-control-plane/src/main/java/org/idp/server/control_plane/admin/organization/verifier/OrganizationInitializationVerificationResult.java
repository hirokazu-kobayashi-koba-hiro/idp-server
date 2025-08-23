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

package org.idp.server.control_plane.admin.organization.verifier;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.admin.organization.io.OrganizationInitializationResponse;
import org.idp.server.control_plane.admin.organization.io.OrganizationInitializationStatus;
import org.idp.server.control_plane.base.verifier.VerificationResult;

public class OrganizationInitializationVerificationResult {
  boolean isValid;
  VerificationResult tenantVerificationResult;
  VerificationResult clientVerificationResult;
  boolean dryRun;

  public static OrganizationInitializationVerificationResult success(
      VerificationResult tenantVerificationResult,
      VerificationResult clientVerificationResult,
      boolean dryRun) {
    return new OrganizationInitializationVerificationResult(
        true, tenantVerificationResult, clientVerificationResult, dryRun);
  }

  public static OrganizationInitializationVerificationResult error(
      VerificationResult tenantVerificationResult,
      VerificationResult clientVerificationResult,
      boolean dryRun) {
    return new OrganizationInitializationVerificationResult(
        false, tenantVerificationResult, clientVerificationResult, dryRun);
  }

  private OrganizationInitializationVerificationResult(
      boolean isValid,
      VerificationResult tenantVerificationResult,
      VerificationResult clientVerificationResult,
      boolean dryRun) {
    this.isValid = isValid;
    this.tenantVerificationResult = tenantVerificationResult;
    this.clientVerificationResult = clientVerificationResult;
    this.dryRun = dryRun;
  }

  public boolean isValid() {
    return isValid;
  }

  public OrganizationInitializationResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "idp-server starter verification is failed");
    Map<String, Object> details = new HashMap<>();

    if (!tenantVerificationResult.isValid()) {
      details.put("tenant", tenantVerificationResult.errors());
    }
    if (!clientVerificationResult.isValid()) {
      details.put("client", clientVerificationResult.errors());
    }

    response.put("details", details);
    return new OrganizationInitializationResponse(
        OrganizationInitializationStatus.INVALID_REQUEST, response);
  }
}

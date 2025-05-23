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

package org.idp.server.control_plane.admin.tenant.verifier;

import org.idp.server.control_plane.admin.tenant.TenantInitializationContext;
import org.idp.server.control_plane.base.verifier.ClientVerifier;
import org.idp.server.control_plane.base.verifier.TenantVerifier;
import org.idp.server.control_plane.base.verifier.VerificationResult;

public class TenantInitializationVerifier {

  TenantVerifier tenantVerifier;
  ClientVerifier clientVerifier;

  public TenantInitializationVerifier(
      TenantVerifier tenantVerifier, ClientVerifier clientVerifier) {
    this.tenantVerifier = tenantVerifier;
    this.clientVerifier = clientVerifier;
  }

  public TenantInitializationVerificationResult verify(TenantInitializationContext context) {

    VerificationResult tenantVerificationResult = tenantVerifier.verify(context.tenant());
    VerificationResult clientVerificationResult =
        clientVerifier.verify(context.tenant(), context.clientConfiguration());

    if (!tenantVerificationResult.isValid() || !clientVerificationResult.isValid()) {
      return TenantInitializationVerificationResult.error(
          tenantVerificationResult, clientVerificationResult, context.isDryRun());
    }

    return TenantInitializationVerificationResult.success(
        tenantVerificationResult, clientVerificationResult, context.isDryRun());
  }
}

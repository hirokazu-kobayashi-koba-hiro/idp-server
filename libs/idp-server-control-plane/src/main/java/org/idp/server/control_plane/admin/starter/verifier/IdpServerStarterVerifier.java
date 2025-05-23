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

package org.idp.server.control_plane.admin.starter.verifier;

import org.idp.server.control_plane.admin.starter.IdpServerStarterContext;
import org.idp.server.control_plane.base.verifier.VerificationResult;

public class IdpServerStarterVerifier {

  StarterTenantVerifier tenantVerifier;

  public IdpServerStarterVerifier(StarterTenantVerifier tenantVerifier) {
    this.tenantVerifier = tenantVerifier;
  }

  public IdpServerVerificationResult verify(IdpServerStarterContext context) {

    VerificationResult verificationResult = tenantVerifier.verify(context.tenant());

    if (!verificationResult.isValid()) {
      return IdpServerVerificationResult.error(verificationResult, context.isDryRun());
    }

    return IdpServerVerificationResult.success(verificationResult, context.isDryRun());
  }
}

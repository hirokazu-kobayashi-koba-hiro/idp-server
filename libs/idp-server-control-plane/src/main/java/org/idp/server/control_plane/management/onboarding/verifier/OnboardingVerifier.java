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

package org.idp.server.control_plane.management.onboarding.verifier;

import org.idp.server.control_plane.base.verifier.TenantVerifier;
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.onboarding.OnboardingContext;

public class OnboardingVerifier {

  TenantVerifier tenantVerifier;

  public OnboardingVerifier(TenantVerifier tenantVerifier) {
    this.tenantVerifier = tenantVerifier;
  }

  public void verify(OnboardingContext context) {
    VerificationResult verificationResult = tenantVerifier.verify(context.tenant());
    throwExceptionIfInvalid(verificationResult);
  }

  void throwExceptionIfInvalid(VerificationResult result) {
    if (!result.isValid()) {
      throw new InvalidRequestException("onboarding verification failed", result.errors());
    }
  }
}

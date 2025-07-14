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

package org.idp.server.core.extension.identity.verification.verifier.application;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.extension.identity.verification.IdentityVerificationApplicationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.plugin.IdentityVerificationApplicationRequestVerifierPluginLoader;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.type.RequestAttributes;

public class IdentityVerificationApplicationRequestVerifiers {

  List<IdentityVerificationApplicationRequestVerifier> verifiers;

  public IdentityVerificationApplicationRequestVerifiers() {
    this.verifiers = new ArrayList<>();
    this.verifiers.add(new DenyDuplicateIdentityVerificationApplicationApplicationVerifier());
    this.verifiers.add(new UnmatchedEmailIdentityVerificationApplicationApplicationVerifier());
    this.verifiers.add(new UnmatchedPhoneIdentityVerificationApplicationApplicationVerifier());
    this.verifiers.add(new ContinuousCustomerDueDiligenceIdentityVerificationApplicationVerifier());
    List<IdentityVerificationApplicationRequestVerifier> loaded =
        IdentityVerificationApplicationRequestVerifierPluginLoader.load();
    this.verifiers.addAll(loaded);
  }

  public IdentityVerificationApplicationRequestVerifiedResult verify(
      Tenant tenant,
      User user,
      IdentityVerificationApplication currentApplication,
      IdentityVerificationApplications previousApplications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfiguration verificationConfiguration) {

    for (IdentityVerificationApplicationRequestVerifier verifier : verifiers) {

      if (!verifier.shouldVerify(
          tenant,
          user,
          currentApplication,
          previousApplications,
          type,
          processes,
          request,
          requestAttributes,
          verificationConfiguration)) {
        continue;
      }

      IdentityVerificationApplicationRequestVerifiedResult verifyResult =
          verifier.verify(
              tenant,
              user,
              currentApplication,
              previousApplications,
              type,
              processes,
              request,
              requestAttributes,
              verificationConfiguration);

      if (verifyResult.isError()) {
        return verifyResult;
      }
    }

    return IdentityVerificationApplicationRequestVerifiedResult.success();
  }
}

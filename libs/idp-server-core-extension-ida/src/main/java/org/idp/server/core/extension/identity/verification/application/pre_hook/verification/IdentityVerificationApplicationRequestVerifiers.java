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

package org.idp.server.core.extension.identity.verification.application.pre_hook.verification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.plugin.IdentityVerificationApplicationRequestVerifierPluginLoader;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfig;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationApplicationRequest;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class IdentityVerificationApplicationRequestVerifiers {

  Map<String, IdentityVerificationApplicationRequestVerifier> verifiers;
  LoggerWrapper log =
      LoggerWrapper.getLogger(IdentityVerificationApplicationRequestVerifiers.class);

  public IdentityVerificationApplicationRequestVerifiers() {
    this.verifiers = new HashMap<>();
    DenyDuplicateIdentityVerificationApplicationVerifier denyDuplicate =
        new DenyDuplicateIdentityVerificationApplicationVerifier();
    this.verifiers.put(denyDuplicate.type(), denyDuplicate);
    UserClaimVerifier userClaimVerifier = new UserClaimVerifier();
    this.verifiers.put(userClaimVerifier.type(), userClaimVerifier);
    Map<String, IdentityVerificationApplicationRequestVerifier> loaded =
        IdentityVerificationApplicationRequestVerifierPluginLoader.load();
    this.verifiers.putAll(loaded);
  }

  public IdentityVerificationApplicationRequestVerifiedResult verifyAll(
      Tenant tenant,
      User user,
      IdentityVerificationApplication currentApplication,
      IdentityVerificationApplications previousApplications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);
    List<IdentityVerificationConfig> verifications = processConfig.preHook().verifications();
    for (IdentityVerificationConfig verificationConfig : verifications) {

      IdentityVerificationApplicationRequestVerifier verifier =
          verifiers.get(verificationConfig.type());
      if (verifier == null) {
        log.warn("IdentityVerification verifier is undefined. type: {}", verificationConfig.type());
        continue;
      }

      log.info(
          "Verifying identity verification application request: {}", verificationConfig.type());
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
              verificationConfig);

      if (verifyResult.isError()) {
        return verifyResult;
      }
    }

    return IdentityVerificationApplicationRequestVerifiedResult.success();
  }
}

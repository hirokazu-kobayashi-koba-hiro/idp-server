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

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.extension.identity.plugin.IdentityVerificationRequestVerifierPluginLoader;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class IdentityVerificationRequestVerifiers {

  List<IdentityVerificationRequestVerifier> verifiers;

  public IdentityVerificationRequestVerifiers() {
    this.verifiers = new ArrayList<>();
    this.verifiers.add(new IdentityVerificationRequestBasicAuthVerifier());
    List<IdentityVerificationRequestVerifier> loaded =
        IdentityVerificationRequestVerifierPluginLoader.load();
    this.verifiers.addAll(loaded);
  }

  public IdentityVerificationRequestVerifiedResult verify(
      Tenant tenant,
      User user,
      IdentityVerificationType type,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfiguration verificationConfiguration) {

    for (IdentityVerificationRequestVerifier verifier : verifiers) {

      if (!verifier.shouldVerify(
          tenant, user, type, request, requestAttributes, verificationConfiguration)) {
        continue;
      }

      IdentityVerificationRequestVerifiedResult verifyResult =
          verifier.verify(
              tenant, user, type, request, requestAttributes, verificationConfiguration);

      if (verifyResult.isError()) {
        return verifyResult;
      }
    }

    return IdentityVerificationRequestVerifiedResult.success();
  }
}

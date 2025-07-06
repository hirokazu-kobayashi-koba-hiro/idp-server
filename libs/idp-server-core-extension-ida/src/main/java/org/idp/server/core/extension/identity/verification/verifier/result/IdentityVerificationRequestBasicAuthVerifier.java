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

package org.idp.server.core.extension.identity.verification.verifier.result;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.extension.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationRegistrationConfiguration;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.type.RequestAttributes;

public class IdentityVerificationRequestBasicAuthVerifier
    implements IdentityVerificationRequestVerifier {

  LoggerWrapper log = LoggerWrapper.getLogger(IdentityVerificationRequestBasicAuthVerifier.class);

  @Override
  public boolean shouldVerify(
      Tenant tenant,
      User user,
      BasicAuth basicAuth,
      IdentityVerificationType type,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationRegistrationConfiguration registrationConfiguration =
        verificationConfiguration.registrationConfiguration();
    return registrationConfiguration.hasBasicAuth();
  }

  @Override
  public IdentityVerificationRequestVerifiedResult verify(
      Tenant tenant,
      User user,
      BasicAuth basicAuth,
      IdentityVerificationType type,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationRegistrationConfiguration registrationConfiguration =
        verificationConfiguration.registrationConfiguration();
    BasicAuth configurationBasicAuth = registrationConfiguration.basicAuth();

    if (!basicAuth.exists()) {

      log.error(
          "TenantId: {} identity-verification: {}. BasicAuth does not exist",
          tenant.identifierValue(),
          type.name());

      List<String> errors = new ArrayList<>();
      errors.add("The identity verification request requires a basic authentication");
      return IdentityVerificationRequestVerifiedResult.failure(errors);
    }

    if (!configurationBasicAuth.equals(basicAuth)) {
      log.error(
          "TenantId: {} identity-verification: {}. unmatch a basic authentication",
          tenant.identifierValue(),
          type.name());

      List<String> errors = new ArrayList<>();
      errors.add("The identity verification request unmatch a basic authentication");
      return IdentityVerificationRequestVerifiedResult.failure(errors);
    }

    return IdentityVerificationRequestVerifiedResult.success();
  }
}

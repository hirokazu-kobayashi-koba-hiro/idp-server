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

package org.idp.server.core.extension.identity.verification.verifier;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UnmatchedEmailIdentityVerificationApplicationVerifier
    implements IdentityVerificationRequestVerifier {

  @Override
  public boolean shouldVerify(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);
    Map<String, Object> verificationSchema = processConfig.requestVerificationSchema();

    if (verificationSchema == null || verificationSchema.isEmpty()) {
      return false;
    }

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(verificationSchema);
    return jsonNodeWrapper.optValueAsBoolean("unmatched_user_claims_email", false);
  }

  @Override
  public IdentityVerificationRequestVerificationResult verify(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);
    Map<String, Object> verificationSchema = processConfig.requestVerificationSchema();
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(verificationSchema);
    JsonNodeWrapper unmatchedUserClaims =
        jsonNodeWrapper.getValueAsJsonNode("unmatched_user_claims_email");

    String property = unmatchedUserClaims.getValueOrEmptyAsString("property");
    String requestValue = request.optValueAsString(property, "");

    if (!requestValue.equals(user.email())) {
      return IdentityVerificationRequestVerificationResult.failure(List.of("Email does not match"));
    }

    return IdentityVerificationRequestVerificationResult.success();
  }
}

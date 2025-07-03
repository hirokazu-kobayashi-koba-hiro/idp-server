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

package org.idp.server.core.extension.identity.verification.delegation.request;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.type.RequestAttributes;

public class UserParameterResolver implements AdditionalRequestParameterResolver {

  public boolean shouldResolve(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);
    Map<String, Object> additionalParameterSchema =
        processConfig.requestAdditionalParameterSchema();

    if (additionalParameterSchema == null || additionalParameterSchema.isEmpty()) {
      return false;
    }

    return additionalParameterSchema.containsKey("user");
  }

  // TODO improve to be more flexible
  @Override
  public Map<String, Object> resolve(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfiguration verificationConfiguration) {

    Map<String, Object> additionalParameters = new HashMap<>();
    additionalParameters.put("user_id", user.sub());
    String providerUserId = user.providerUserId();
    if (user.hasProviderUserId()) {
      additionalParameters.put("provider_user_id", providerUserId);
    }

    return additionalParameters;
  }
}

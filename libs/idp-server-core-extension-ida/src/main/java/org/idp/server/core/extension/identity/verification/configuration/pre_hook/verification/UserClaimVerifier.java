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

package org.idp.server.core.extension.identity.verification.configuration.pre_hook.verification;

import java.util.List;
import org.idp.server.core.extension.identity.verification.IdentityVerificationApplicationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfig;
import org.idp.server.core.extension.identity.verification.configuration.pre_hook.UserClaimPreHook;
import org.idp.server.core.extension.identity.verification.configuration.pre_hook.UserClaimVerificationRule;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.type.RequestAttributes;

public class UserClaimVerifier implements IdentityVerificationApplicationRequestVerifier {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public String type() {
    return "user_claim";
  }

  @Override
  public IdentityVerificationApplicationRequestVerifiedResult verify(
      Tenant tenant,
      User user,
      IdentityVerificationApplication currentApplication,
      IdentityVerificationApplications previousApplications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfig verificationConfig) {

    String requestJson = request.toJson();
    String userJson = jsonConverter.write(user);
    JsonPathWrapper requestJsonPath = new JsonPathWrapper(requestJson);
    JsonPathWrapper userJsonPath = new JsonPathWrapper(userJson);
    UserClaimPreHook userClaimPreHook =
        jsonConverter.read(verificationConfig.details(), UserClaimPreHook.class);
    List<UserClaimVerificationRule> userClaimVerificationRules =
        userClaimPreHook.verificationParameters();
    for (UserClaimVerificationRule userClaimVerificationRule : userClaimVerificationRules) {
      Object requestValue = requestJsonPath.readRaw(userClaimVerificationRule.requestJsonPath());
      Object userValue = userJsonPath.readRaw(userClaimVerificationRule.userClaimJsonPath());
    }

    return IdentityVerificationApplicationRequestVerifiedResult.success();
  }
}

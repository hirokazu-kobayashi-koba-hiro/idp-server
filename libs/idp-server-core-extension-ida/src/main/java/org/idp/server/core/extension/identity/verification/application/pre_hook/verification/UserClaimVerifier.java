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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.application.pre_hook.UserClaimPreHook;
import org.idp.server.core.extension.identity.verification.application.pre_hook.UserClaimVerificationRule;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfig;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.condition.ConditionOperation;
import org.idp.server.platform.condition.ConditionOperationEvaluator;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class UserClaimVerifier implements IdentityVerificationApplicationRequestVerifier {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(UserClaimVerifier.class);

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
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfig verificationConfig,
      IdentityVerificationConfiguration verificationConfiguration) {

    String requestJson = request.toJson();
    String userJson = jsonConverter.write(user);
    JsonPathWrapper requestJsonPath = new JsonPathWrapper(requestJson);
    JsonPathWrapper userJsonPath = new JsonPathWrapper(userJson);
    UserClaimPreHook userClaimPreHook =
        jsonConverter.read(verificationConfig.details(), UserClaimPreHook.class);
    List<UserClaimVerificationRule> userClaimVerificationRules =
        userClaimPreHook.verificationParameters();

    List<String> errors = new ArrayList<>();
    for (UserClaimVerificationRule userClaimVerificationRule : userClaimVerificationRules) {
      Object requestValue = requestJsonPath.readRaw(userClaimVerificationRule.requestJsonPath());
      Object userValue = userJsonPath.readRaw(userClaimVerificationRule.userClaimJsonPath());

      // operation defaults to eq -> ConditionOperationEvaluator.EQ delegates to Objects.equals,
      // preserving the original exact-match behavior for rules without an operation.
      String operationValue =
          userClaimVerificationRule.hasOperation() ? userClaimVerificationRule.operation() : "eq";
      ConditionOperation operation = ConditionOperation.from(operationValue);

      // An unknown operation is a tenant misconfiguration, not a request problem. Fail loudly with
      // a
      // distinct error and error-level log rather than silently always-failing every request.
      if (operation == ConditionOperation.UNKNOWN) {
        log.error(
            "User claim verification misconfigured: unknown operation={}, request_path={}, user_path={}",
            operationValue,
            userClaimVerificationRule.requestJsonPath(),
            userClaimVerificationRule.userClaimJsonPath());
        errors.add(
            String.format(
                "User claim verification failed. unknown operation: %s, request:%s, user:%s",
                operationValue,
                userClaimVerificationRule.requestJsonPath(),
                userClaimVerificationRule.userClaimJsonPath()));
        continue;
      }

      // The rule asserts the comparison must hold (e.g. in -> requestValue is a member of the user
      // collection). A null/absent requestValue does not satisfy in/nin/contains, so an unheld key
      // is rejected as 400 pre_hook_validation_failed rather than silently passing.
      if (!ConditionOperationEvaluator.evaluate(requestValue, operation, userValue)) {
        log.warn(
            "User claim mismatch: operation={}, request_path={}, user_path={}, request_value={}, user_value={}",
            operationValue,
            userClaimVerificationRule.requestJsonPath(),
            userClaimVerificationRule.userClaimJsonPath(),
            maskSensitiveValue(requestValue),
            maskSensitiveValue(userValue));
        errors.add(
            String.format(
                "User claim verification failed. unmatched: %s, user:%s",
                userClaimVerificationRule.requestJsonPath(),
                userClaimVerificationRule.userClaimJsonPath()));
      }
    }

    if (!errors.isEmpty()) {
      log.info(
          "User claim verification failed: type={}, process={}, error_count={}",
          type.name(),
          processes.name(),
          errors.size());
      return IdentityVerificationApplicationRequestVerifiedResult.failure(errors);
    }

    return IdentityVerificationApplicationRequestVerifiedResult.success();
  }

  private static Object maskSensitiveValue(Object value) {
    if (value instanceof String str) {
      if (str.length() > 50) {
        return "***";
      }
    }
    // membership operators compare against a user collection (e.g. an array of ids that may carry
    // PII); summarize it by size rather than dumping the elements into logs.
    if (value instanceof Collection<?> collection) {
      return "[collection size=" + collection.size() + "]";
    }
    return value;
  }
}

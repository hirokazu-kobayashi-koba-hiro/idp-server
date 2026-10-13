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
package org.idp.server.core.openid.authentication.interaction.post_hook;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.openid.authentication.config.AuthenticationVerificationConfig;
import org.idp.server.platform.condition.ConditionOperation;
import org.idp.server.platform.condition.ConditionOperationEvaluator;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Compares the values at two JSONPaths of the context.
 *
 * <p>Example {@code details}:
 *
 * <pre>{@code
 * {
 *   "rules": [
 *     {
 *       "path": "$.execution_http_request.response_body.user_id",
 *       "operation": "eq",
 *       "target_path": "$.interaction.device_id"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>A rule fails when either value is missing, so a path that does not resolve never passes the
 * verification. {@code operation} defaults to {@code eq}. A path such as {@code [*]} resolves to a
 * list, and an empty list is a value, so choose the operation with that in mind.
 *
 * <p>A misconfiguration, such as malformed {@code details}, an invalid JSONPath or an operation
 * that does not fit the values, fails the verification instead of raising an error.
 */
public class MatchVerifier implements AuthenticationPostHookVerifier {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  LoggerWrapper log = LoggerWrapper.getLogger(MatchVerifier.class);

  @Override
  public String type() {
    return "match";
  }

  @Override
  public AuthenticationPostHookVerificationResult verify(
      AuthenticationVerificationConfig verificationConfig, JsonPathWrapper context) {

    MatchVerificationDetails details;
    try {
      details = jsonConverter.read(verificationConfig.details(), MatchVerificationDetails.class);
    } catch (RuntimeException e) {
      log.error(
          "match verification misconfigured: invalid details. {}", e.getClass().getSimpleName());
      return misconfigured("match verification has invalid details.");
    }

    if (!details.hasRules()) {
      log.error("match verification misconfigured: no rules");
      return misconfigured("match verification has no rules.");
    }

    List<String> errors = new ArrayList<>();
    for (MatchVerificationRule rule : details.rules()) {
      if (rule == null || !rule.hasPaths()) {
        log.error("match verification misconfigured: path or target_path is missing");
        errors.add("match rule is misconfigured. path and target_path are required.");
        continue;
      }

      if (ConditionOperation.from(rule.operation()) == ConditionOperation.UNKNOWN) {
        log.error("match verification misconfigured: unknown operation={}", rule.operation());
        errors.add(String.format("match rule has an unknown operation: %s", rule.operation()));
        continue;
      }

      if (!matches(rule, context)) {
        errors.add(
            String.format("unmatched. path:%s, target_path:%s", rule.path(), rule.targetPath()));
      }
    }

    if (!errors.isEmpty()) {
      return AuthenticationPostHookVerificationResult.failure(errors);
    }
    return AuthenticationPostHookVerificationResult.success();
  }

  /** Evaluates a rule. A missing value or an error while evaluating counts as unmatched. */
  private boolean matches(MatchVerificationRule rule, JsonPathWrapper context) {
    try {
      Object value = context.readRaw(rule.path());
      Object targetValue = context.readRaw(rule.targetPath());

      if (value == null
          || targetValue == null
          || !ConditionOperationEvaluator.evaluate(value, rule.operation(), targetValue)) {
        log.warn(
            "match verification failed: path={}, operation={}, target_path={}, value_exists={}, target_value_exists={}",
            rule.path(),
            rule.operation(),
            rule.targetPath(),
            value != null,
            targetValue != null);
        return false;
      }
      return true;
    } catch (RuntimeException e) {
      log.error(
          "match verification misconfigured: path={}, operation={}, target_path={}. {}",
          rule.path(),
          rule.operation(),
          rule.targetPath(),
          e.getClass().getSimpleName());
      return false;
    }
  }

  private AuthenticationPostHookVerificationResult misconfigured(String error) {
    return AuthenticationPostHookVerificationResult.failure(List.of(error));
  }
}

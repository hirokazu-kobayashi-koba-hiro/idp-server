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
import java.util.Map;
import org.idp.server.core.openid.authentication.config.AuthenticationVerificationConfig;
import org.idp.server.platform.condition.ConditionOperation;
import org.idp.server.platform.condition.ConditionOperationEvaluator;
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
 * verification. {@code operation} defaults to {@code eq}.
 */
public class MatchVerifier implements AuthenticationPostHookVerifier {

  LoggerWrapper log = LoggerWrapper.getLogger(MatchVerifier.class);

  @Override
  public String type() {
    return "match";
  }

  @Override
  public AuthenticationPostHookVerificationResult verify(
      AuthenticationVerificationConfig verificationConfig, JsonPathWrapper context) {

    List<Map<String, Object>> rules = rules(verificationConfig.details());
    if (rules.isEmpty()) {
      log.error("match verification misconfigured: no rules");
      return AuthenticationPostHookVerificationResult.failure(
          List.of("match verification has no rules."));
    }

    List<String> errors = new ArrayList<>();
    for (Map<String, Object> rule : rules) {
      String path = (String) rule.get("path");
      String targetPath = (String) rule.get("target_path");
      String operation = (String) rule.getOrDefault("operation", "eq");

      if (path == null || targetPath == null) {
        log.error("match verification misconfigured: path={}, target_path={}", path, targetPath);
        errors.add(
            String.format(
                "match rule is misconfigured. path:%s, target_path:%s", path, targetPath));
        continue;
      }

      if (ConditionOperation.from(operation) == ConditionOperation.UNKNOWN) {
        log.error("match verification misconfigured: unknown operation={}", operation);
        errors.add(String.format("match rule has an unknown operation: %s", operation));
        continue;
      }

      Object value = context.readRaw(path);
      Object targetValue = context.readRaw(targetPath);

      if (value == null
          || targetValue == null
          || !ConditionOperationEvaluator.evaluate(value, operation, targetValue)) {
        log.warn(
            "match verification failed: path={}, operation={}, target_path={}, value_exists={}, target_value_exists={}",
            path,
            operation,
            targetPath,
            value != null,
            targetValue != null);
        errors.add(String.format("unmatched. path:%s, target_path:%s", path, targetPath));
      }
    }

    if (!errors.isEmpty()) {
      return AuthenticationPostHookVerificationResult.failure(errors);
    }
    return AuthenticationPostHookVerificationResult.success();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> rules(Map<String, Object> details) {
    Object rules = details.get("rules");
    if (!(rules instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object rule : list) {
      if (rule instanceof Map<?, ?> map) {
        result.add((Map<String, Object>) map);
      }
    }
    return result;
  }
}

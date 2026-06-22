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

package org.idp.server.core.openid.authentication.evaluator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.AuthenticationInteractionResults;
import org.idp.server.core.openid.authentication.policy.AuthenticationResultCondition;
import org.idp.server.core.openid.authentication.policy.AuthenticationResultConditionConfig;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.condition.ConditionOperationEvaluator;
import org.idp.server.platform.condition.ConditionTransitionResult;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;

public class MfaConditionEvaluator {

  public static boolean isSuccessSatisfied(
      AuthenticationResultConditionConfig config, AuthenticationInteractionResults results) {
    return isSuccessSatisfied(config, results, User.notFound());
  }

  /**
   * Issue #1501: evaluates success conditions with the transaction user exposed as {@code
   * $.user.*}. The {@code user} projection is allow-listed (see {@link
   * PolicyEvaluationUserContextCreator}).
   */
  public static boolean isSuccessSatisfied(
      AuthenticationResultConditionConfig config,
      AuthenticationInteractionResults results,
      User user) {
    if (!config.exists() || !results.exists()) {
      return false;
    }

    ConditionTransitionResult result = isAnySatisfied(config, buildContext(results, user));

    return result.isSuccess();
  }

  public static boolean isFailureSatisfied(
      AuthenticationResultConditionConfig config, AuthenticationInteractionResults results) {
    return isFailureSatisfied(config, results, User.notFound());
  }

  /**
   * Issue #1501: evaluates failure conditions with the transaction user exposed as {@code
   * $.user.*}.
   */
  public static boolean isFailureSatisfied(
      AuthenticationResultConditionConfig config,
      AuthenticationInteractionResults results,
      User user) {
    if (!config.exists() || !results.exists()) {
      return false;
    }

    if (results.containsDenyInteraction()) {
      return true;
    }

    ConditionTransitionResult result = isAnySatisfied(config, buildContext(results, user));

    return result.isSuccess();
  }

  public static boolean isLockedSatisfied(
      AuthenticationResultConditionConfig config, AuthenticationInteractionResults results) {
    return isLockedSatisfied(config, results, User.notFound());
  }

  /**
   * Issue #1501: evaluates lock conditions with the transaction user exposed as {@code $.user.*}.
   */
  public static boolean isLockedSatisfied(
      AuthenticationResultConditionConfig config,
      AuthenticationInteractionResults results,
      User user) {
    if (!config.exists() || !results.exists()) {
      return false;
    }

    ConditionTransitionResult result = isAnySatisfied(config, buildContext(results, user));

    return result.isSuccess();
  }

  /**
   * Builds the JsonPath evaluation context. Existing interaction-result paths (e.g. {@code
   * $.password-authentication.success_count}) are preserved as-is; the allow-listed user projection
   * is added under the {@code user} key so conditions can reference {@code $.user.*} (Issue #1501).
   */
  private static Map<String, Object> buildContext(
      AuthenticationInteractionResults results, User user) {
    Map<String, Object> context = new HashMap<>(results.toMapAsObject());
    context.put("user", PolicyEvaluationUserContextCreator.create(user));
    return context;
  }

  static ConditionTransitionResult isAnySatisfied(
      AuthenticationResultConditionConfig conditionConfig, Map<String, Object> request) {

    if (!conditionConfig.exists()) {
      return ConditionTransitionResult.UNDEFINED;
    }

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(request);
    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());

    for (List<AuthenticationResultCondition> resultConditions : conditionConfig.anyOf()) {

      if (isAllSatisfied(resultConditions, jsonPathWrapper)) {
        return ConditionTransitionResult.SUCCESS;
      }
    }

    return ConditionTransitionResult.FAILURE;
  }

  static boolean isAllSatisfied(
      List<AuthenticationResultCondition> resultConditions, JsonPathWrapper jsonPathWrapper) {
    for (AuthenticationResultCondition resultCondition : resultConditions) {

      Object actualValue = jsonPathWrapper.readRaw(resultCondition.path());

      if (!ConditionOperationEvaluator.evaluate(
          actualValue, resultCondition.operation(), resultCondition.value())) {
        return false;
      }
    }
    return true;
  }
}

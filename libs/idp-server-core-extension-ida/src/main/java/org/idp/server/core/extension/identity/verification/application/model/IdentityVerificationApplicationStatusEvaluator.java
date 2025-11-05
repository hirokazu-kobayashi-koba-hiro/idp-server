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

package org.idp.server.core.extension.identity.verification.application.model;

import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationContext;
import org.idp.server.core.extension.identity.verification.configuration.process.*;
import org.idp.server.platform.condition.ConditionOperationEvaluator;
import org.idp.server.platform.condition.ConditionTransitionResult;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;

public class IdentityVerificationApplicationStatusEvaluator {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(IdentityVerificationApplicationStatusEvaluator.class);

  public static IdentityVerificationApplicationStatus evaluateOnProcess(
      IdentityVerificationProcessConfiguration config, IdentityVerificationContext context) {

    log.debug("Status evaluation started (on process)");

    IdentityVerificationConditionConfig approvedConfig = config.approved();
    ConditionTransitionResult approvedRequestResult =
        isAnySatisfied(approvedConfig, context.toMap());

    if (approvedRequestResult.isSuccess()) {
      log.info("Status transition: → APPROVED");
      return IdentityVerificationApplicationStatus.APPROVED;
    }

    IdentityVerificationConditionConfig rejectedConfig = config.rejected();
    ConditionTransitionResult rejectedRequestResult =
        isAnySatisfied(rejectedConfig, context.toMap());

    if (rejectedRequestResult.isSuccess()) {
      log.info("Status transition: → REJECTED");
      return IdentityVerificationApplicationStatus.REJECTED;
    }

    IdentityVerificationConditionConfig cancellationConfig = config.canceled();
    ConditionTransitionResult cancellationRequestResult =
        isAnySatisfied(cancellationConfig, context.toMap());

    if (cancellationRequestResult.isSuccess()) {
      log.info("Status transition: → CANCELLED");
      return IdentityVerificationApplicationStatus.CANCELLED;
    }

    IdentityVerificationConditionConfig appliedConfig = config.applied();
    ConditionTransitionResult appliedRequestResult = isAnySatisfied(appliedConfig, context.toMap());

    if (appliedRequestResult.isSuccess()) {
      log.info("Status transition: → APPLIED");
      return IdentityVerificationApplicationStatus.APPLIED;
    }

    log.debug(
        "Status remains: APPLYING, evaluated_conditions=[approved={}, rejected={}, "
            + "canceled={}, applied={}]",
        approvedRequestResult,
        rejectedRequestResult,
        cancellationRequestResult,
        appliedRequestResult);
    return IdentityVerificationApplicationStatus.APPLYING;
  }

  public static IdentityVerificationApplicationStatus evaluateOnCallback(
      IdentityVerificationProcessConfiguration config, IdentityVerificationContext context) {

    log.debug("Status evaluation started (on callback)");

    IdentityVerificationConditionConfig approvedConfig = config.approved();
    ConditionTransitionResult approvedRequestResult =
        isAnySatisfied(approvedConfig, context.toMap());

    if (approvedRequestResult.isSuccess()) {
      log.info("Status transition (callback): → APPROVED");
      return IdentityVerificationApplicationStatus.APPROVED;
    }

    IdentityVerificationConditionConfig rejectedConfig = config.rejected();
    ConditionTransitionResult rejectedRequestResult =
        isAnySatisfied(rejectedConfig, context.toMap());

    if (rejectedRequestResult.isSuccess()) {
      log.info("Status transition (callback): → REJECTED");
      return IdentityVerificationApplicationStatus.REJECTED;
    }

    IdentityVerificationConditionConfig cancellationConfig = config.canceled();
    ConditionTransitionResult cancellationRequestResult =
        isAnySatisfied(cancellationConfig, context.toMap());

    if (cancellationRequestResult.isSuccess()) {
      log.info("Status transition (callback): → CANCELLED");
      return IdentityVerificationApplicationStatus.CANCELLED;
    }

    IdentityVerificationConditionConfig appliedConfig = config.applied();
    ConditionTransitionResult appliedRequestResult = isAnySatisfied(appliedConfig, context.toMap());

    if (appliedRequestResult.isSuccess()) {
      log.info("Status transition (callback): → APPLIED");
      return IdentityVerificationApplicationStatus.APPLIED;
    }

    log.debug(
        "Status remains: EXAMINATION_PROCESSING, evaluated_conditions=[approved={}, "
            + "rejected={}, canceled={}, applied={}]",
        approvedRequestResult,
        rejectedRequestResult,
        cancellationRequestResult,
        appliedRequestResult);
    return IdentityVerificationApplicationStatus.EXAMINATION_PROCESSING;
  }

  static ConditionTransitionResult isAnySatisfied(
      IdentityVerificationConditionConfig conditionConfig, Map<String, Object> request) {

    if (!conditionConfig.exists()) {
      return ConditionTransitionResult.UNDEFINED;
    }

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(request);
    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());

    for (List<IdentityVerificationCondition> resultConditions : conditionConfig.anyOf()) {

      if (isAllSatisfied(resultConditions, jsonPathWrapper)) {
        return ConditionTransitionResult.SUCCESS;
      }
    }

    return ConditionTransitionResult.FAILURE;
  }

  static boolean isAllSatisfied(
      List<IdentityVerificationCondition> resultConditions, JsonPathWrapper jsonPathWrapper) {
    log.debug("Evaluating AND conditions: count={}", resultConditions.size());

    for (int i = 0; i < resultConditions.size(); i++) {
      IdentityVerificationCondition resultCondition = resultConditions.get(i);

      Object actualValue = jsonPathWrapper.readRaw(resultCondition.path());
      boolean result =
          ConditionOperationEvaluator.evaluate(
              actualValue, resultCondition.operation(), resultCondition.value());

      log.debug(
          "Condition[{}]: path={}, operation={}, expected={}, actual={}, result={}",
          i,
          resultCondition.path(),
          resultCondition.operation(),
          maskSensitiveValue(resultCondition.value()),
          maskSensitiveValue(actualValue),
          result);

      if (!result) {
        log.debug(
            "AND condition group failed at index {}: path={}, expected={}, actual={}",
            i,
            resultCondition.path(),
            maskSensitiveValue(resultCondition.value()),
            maskSensitiveValue(actualValue));
        return false;
      }
    }

    log.debug("All AND conditions satisfied: count={}", resultConditions.size());
    return true;
  }

  private static Object maskSensitiveValue(Object value) {
    if (value instanceof String str) {
      if (str.contains("token") || str.contains("password") || str.length() > 50) {
        return "***";
      }
    }
    return value;
  }
}

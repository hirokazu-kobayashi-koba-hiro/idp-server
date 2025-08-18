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

public class IdentityVerificationApplicationStatusEvaluator {

  public static IdentityVerificationApplicationStatus evaluateOnProcess(
      IdentityVerificationProcessConfiguration config, IdentityVerificationContext context) {

    IdentityVerificationConditionConfig approvedConfig = config.approved();
    ConditionTransitionResult approvedRequestResult =
        isAnySatisfied(approvedConfig, context.toMap());

    if (approvedRequestResult.isSuccess()) {
      return IdentityVerificationApplicationStatus.APPROVED;
    }

    IdentityVerificationConditionConfig rejectedConfig = config.rejected();
    ConditionTransitionResult rejectedRequestResult =
        isAnySatisfied(rejectedConfig, context.toMap());

    if (rejectedRequestResult.isSuccess()) {
      return IdentityVerificationApplicationStatus.REJECTED;
    }

    IdentityVerificationConditionConfig cancellationConfig = config.canceled();
    ConditionTransitionResult cancellationRequestResult =
        isAnySatisfied(cancellationConfig, context.toMap());

    if (cancellationRequestResult.isSuccess()) {
      return IdentityVerificationApplicationStatus.CANCELLED;
    }

    return IdentityVerificationApplicationStatus.APPLYING;
  }

  public static IdentityVerificationApplicationStatus evaluateOnCallback(
      IdentityVerificationProcessConfiguration config, IdentityVerificationContext context) {

    IdentityVerificationConditionConfig approvedConfig = config.approved();
    ConditionTransitionResult approvedRequestResult =
        isAnySatisfied(approvedConfig, context.toMap());

    if (approvedRequestResult.isSuccess()) {
      return IdentityVerificationApplicationStatus.APPROVED;
    }

    IdentityVerificationConditionConfig rejectedConfig = config.rejected();
    ConditionTransitionResult rejectedRequestResult =
        isAnySatisfied(rejectedConfig, context.toMap());

    if (rejectedRequestResult.isSuccess()) {
      return IdentityVerificationApplicationStatus.REJECTED;
    }

    IdentityVerificationConditionConfig cancellationConfig = config.canceled();
    ConditionTransitionResult cancellationRequestResult =
        isAnySatisfied(cancellationConfig, context.toMap());

    if (cancellationRequestResult.isSuccess()) {
      return IdentityVerificationApplicationStatus.CANCELLED;
    }

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
    for (IdentityVerificationCondition resultCondition : resultConditions) {

      Object actualValue = jsonPathWrapper.readRaw(resultCondition.path());

      if (!ConditionOperationEvaluator.evaluate(
          actualValue, resultCondition.operation(), resultCondition.value())) {
        return false;
      }
    }
    return true;
  }
}

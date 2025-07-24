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
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationApplicationContext;
import org.idp.server.core.extension.identity.verification.configuration.process.*;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;

public class IdentityVerificationApplicationStatusEvaluator {

  public static IdentityVerificationApplicationStatus evaluateOnProcess(
      IdentityVerificationProcessConfiguration config,
      IdentityVerificationApplicationContext context) {

    IdentityVerificationConditionConfig approvedConfig = config.approved();
    IdentityVerificationTransitionResult approvedRequestResult =
        isAnySatisfied(approvedConfig, context.toMap());

    if (approvedRequestResult.isSuccess()) {
      return IdentityVerificationApplicationStatus.APPROVED;
    }

    IdentityVerificationConditionConfig rejectedConfig = config.rejected();
    IdentityVerificationTransitionResult rejectedRequestResult =
        isAnySatisfied(rejectedConfig, context.toMap());

    if (rejectedRequestResult.isSuccess()) {
      return IdentityVerificationApplicationStatus.REJECTED;
    }

    IdentityVerificationConditionConfig cancellationConfig = config.canceled();
    IdentityVerificationTransitionResult cancellationRequestResult =
        isAnySatisfied(cancellationConfig, context.toMap());

    if (cancellationRequestResult.isSuccess()) {
      return IdentityVerificationApplicationStatus.CANCELLED;
    }

    return IdentityVerificationApplicationStatus.APPLYING;
  }

  public static IdentityVerificationApplicationStatus evaluateOnCallback(
      IdentityVerificationProcessConfiguration config,
      IdentityVerificationApplicationContext context) {

    IdentityVerificationConditionConfig approvedConfig = config.approved();
    IdentityVerificationTransitionResult approvedRequestResult =
        isAnySatisfied(approvedConfig, context.toMap());

    if (approvedRequestResult.isSuccess()) {
      return IdentityVerificationApplicationStatus.APPROVED;
    }

    IdentityVerificationConditionConfig rejectedConfig = config.rejected();
    IdentityVerificationTransitionResult rejectedRequestResult =
        isAnySatisfied(rejectedConfig, context.toMap());

    if (rejectedRequestResult.isSuccess()) {
      return IdentityVerificationApplicationStatus.REJECTED;
    }

    IdentityVerificationConditionConfig cancellationConfig = config.canceled();
    IdentityVerificationTransitionResult cancellationRequestResult =
        isAnySatisfied(cancellationConfig, context.toMap());

    if (cancellationRequestResult.isSuccess()) {
      return IdentityVerificationApplicationStatus.CANCELLED;
    }

    return IdentityVerificationApplicationStatus.EXAMINATION_PROCESSING;
  }

  static IdentityVerificationTransitionResult isAnySatisfied(
      IdentityVerificationConditionConfig conditionConfig, Map<String, Object> request) {

    if (!conditionConfig.exists()) {
      return IdentityVerificationTransitionResult.UNDEFINED;
    }

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(request);
    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());

    for (List<IdentityVerificationCondition> resultConditions : conditionConfig.anyOf()) {

      if (isAllSatisfied(resultConditions, jsonPathWrapper)) {
        return IdentityVerificationTransitionResult.SUCCESS;
      }
    }

    return IdentityVerificationTransitionResult.FAILURE;
  }

  static boolean isAllSatisfied(
      List<IdentityVerificationCondition> resultConditions, JsonPathWrapper jsonPathWrapper) {
    for (IdentityVerificationCondition resultCondition : resultConditions) {

      Object actualValue = jsonPathWrapper.readRaw(resultCondition.path());

      if (!OperatorEvaluator.evaluate(
          actualValue, resultCondition.operation(), resultCondition.value())) {
        return false;
      }
    }
    return true;
  }
}

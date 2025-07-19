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

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.extension.identity.verification.configuration.process.*;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationApplicationRequest;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationCallbackRequest;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;

public class IdentityVerificationApplicationStatusEvaluator {

  public static IdentityVerificationApplicationStatus evaluate(
      IdentityVerificationProcessConfiguration config,
      IdentityVerificationApplicationRequest request,
      IdentityVerificationApplicationProcessResults results) {

    IdentityVerificationConditionConfig completionConfig = config.completionCondition();
    boolean completionRequestResult =
        isSatisfiedRequest(completionConfig.requestCondition(), request.toMap());
    boolean completionProcessResult =
        isSatisfiedProcess(completionConfig.processCondition(), results);

    if (completionRequestResult && completionProcessResult) {
      return IdentityVerificationApplicationStatus.APPROVED;
    }

    IdentityVerificationConditionConfig rejectedConfig = config.rejectionCondition();
    boolean rejectedRequestResult =
        isSatisfiedRequest(rejectedConfig.requestCondition(), request.toMap());
    boolean rejectedProcessResult = isSatisfiedProcess(rejectedConfig.processCondition(), results);

    if (rejectedRequestResult && rejectedProcessResult) {
      return IdentityVerificationApplicationStatus.REJECTED;
    }

    IdentityVerificationConditionConfig cancellationConfig = config.cancellationCondition();
    boolean cancellationRequestResult =
        isSatisfiedRequest(cancellationConfig.requestCondition(), request.toMap());
    boolean cancellationProcessResult =
        isSatisfiedProcess(cancellationConfig.processCondition(), results);

    if (cancellationRequestResult && cancellationProcessResult) {
      return IdentityVerificationApplicationStatus.CANCELLED;
    }

    return IdentityVerificationApplicationStatus.APPLYING;
  }

  public static IdentityVerificationApplicationStatus evaluate(
      IdentityVerificationProcessConfiguration config,
      IdentityVerificationCallbackRequest request,
      IdentityVerificationApplicationProcessResults results) {

    IdentityVerificationConditionConfig completionConfig = config.completionCondition();
    boolean completionRequestResult =
        isSatisfiedRequest(completionConfig.requestCondition(), request.toMap());
    boolean completionProcessResult =
        isSatisfiedProcess(completionConfig.processCondition(), results);

    if (completionRequestResult && completionProcessResult) {
      return IdentityVerificationApplicationStatus.APPROVED;
    }

    IdentityVerificationConditionConfig rejectedConfig = config.rejectionCondition();
    boolean rejectedRequestResult =
        isSatisfiedRequest(rejectedConfig.requestCondition(), request.toMap());
    boolean rejectedProcessResult = isSatisfiedProcess(rejectedConfig.processCondition(), results);

    if (rejectedRequestResult && rejectedProcessResult) {
      return IdentityVerificationApplicationStatus.REJECTED;
    }

    IdentityVerificationConditionConfig cancellationConfig = config.cancellationCondition();
    boolean cancellationRequestResult =
        isSatisfiedRequest(cancellationConfig.requestCondition(), request.toMap());
    boolean cancellationProcessResult =
        isSatisfiedProcess(cancellationConfig.processCondition(), results);

    if (cancellationRequestResult && cancellationProcessResult) {
      return IdentityVerificationApplicationStatus.CANCELLED;
    }

    return IdentityVerificationApplicationStatus.EXAMINATION_PROCESSING;
  }

  static boolean isSatisfiedProcess(
      IdentityVerificationProcessConditionConfig processCondition,
      IdentityVerificationApplicationProcessResults results) {

    if (!processCondition.exists()) {
      return false;
    }

    if (processCondition.hasAllOf()) {
      for (IdentityVerificationProcessResultCondition condition : processCondition.allOf()) {
        if (!results.contains(condition.processName())) return false;
        if (condition.isSuccessType()
            && !condition.isGraterEqualSuccessCount(
                results.get(condition.processName()).successCount())) return false;
        if (condition.isFailureType()
            && !condition.isGraterEqualFailureCount(
                results.get(condition.processName()).failureCount())) return false;
      }
      return true;
    }

    if (processCondition.hasAnyOf()) {
      for (IdentityVerificationProcessResultCondition condition : processCondition.anyOf()) {
        if (!results.contains(condition.processName())) continue;
        if (condition.isSuccessType()
            && condition.isGraterEqualSuccessCount(
                results.get(condition.processName()).successCount())) return true;
        if (condition.isFailureType()
            && condition.isGraterEqualFailureCount(
                results.get(condition.processName()).failureCount())) return true;
      }
      return false;
    }

    return false;
  }

  static boolean isSatisfiedRequest(
      IdentityVerificationRequestConditionConfig conditionConfig, Map<String, Object> request) {

    if (!conditionConfig.exists()) {
      return false;
    }

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(request);
    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
    for (IdentityVerificationRequestResultCondition resultCondition : conditionConfig.allOf()) {

      Object actualValue =
          extractValue(jsonPathWrapper, resultCondition.path(), resultCondition.type());

      if (!OperatorEvaluator.evaluate(
          actualValue, resultCondition.operation(), resultCondition.value())) {
        return false;
      }
    }

    for (IdentityVerificationRequestResultCondition resultCondition : conditionConfig.anyOf()) {

      Object actualValue =
          extractValue(jsonPathWrapper, resultCondition.path(), resultCondition.type());

      if (OperatorEvaluator.evaluate(
          actualValue, resultCondition.operation(), resultCondition.value())) {
        return true;
      }
    }

    return true;
  }

  static Object extractValue(JsonPathWrapper jsonPathWrapper, String path, String type) {
    if (jsonPathWrapper == null) return null;

    if (Objects.equals(type, "string")) return jsonPathWrapper.readAsString(path);
    if (Objects.equals(type, "integer")) return jsonPathWrapper.readAsInt(path);
    if (Objects.equals(type, "boolean")) return jsonPathWrapper.readAsBoolean(path);

    return null;
  }
}

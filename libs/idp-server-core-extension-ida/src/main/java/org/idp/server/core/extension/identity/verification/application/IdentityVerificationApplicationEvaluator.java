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

package org.idp.server.core.extension.identity.verification.application;

import java.util.Objects;
import org.idp.server.core.extension.identity.verification.IdentityVerificationApplicationRequest;
import org.idp.server.core.extension.identity.verification.configuration.*;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;

public class IdentityVerificationApplicationEvaluator {

  public static boolean isSatisfied(
      IdentityVerificationConditionConfiguration config,
      IdentityVerificationApplicationRequest applicationRequest,
      IdentityVerificationApplicationProcessResults results) {

    if (!config.exists()) {
      return false;
    }

    boolean requestResult = isSatisfiedRequest(config.requestCondition(), applicationRequest);
    boolean processResult = isSatisfiedProcess(config.processCondition(), results);

    return requestResult && processResult;
  }

  static boolean isSatisfiedProcess(
      IdentityVerificationProcessConditionConfig processCondition,
      IdentityVerificationApplicationProcessResults results) {

    if (!processCondition.exists()) {
      return true;
    }

    if (processCondition.hasAllOf()) {
      for (IdentityVerificationProcessResultCondition condition : processCondition.allOf()) {
        if (!results.contains(condition.type())) return false;
        if (condition.isSuccessType()
            && !condition.isSatisfiedCount(results.get(condition.processName()).successCount()))
          return false;
        if (condition.isFailureType()
            && !condition.isSatisfiedCount(results.get(condition.processName()).failureCount()))
          return false;
      }
      return true;
    }

    if (processCondition.hasAnyOf()) {
      for (IdentityVerificationProcessResultCondition condition : processCondition.anyOf()) {
        if (!results.contains(condition.type())) continue;
        if (condition.isSuccessType()
            && !condition.isSatisfiedCount(results.get(condition.processName()).successCount()))
          return true;
        if (condition.isFailureType()
            && !condition.isSatisfiedCount(results.get(condition.processName()).failureCount()))
          return true;
      }
      return false;
    }

    return false;
  }

  static boolean isSatisfiedRequest(
      IdentityVerificationRequestConditionConfig conditionConfig,
      IdentityVerificationApplicationRequest applicationRequest) {

    if (!conditionConfig.exists()) {
      return true;
    }

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(applicationRequest.toMap());
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

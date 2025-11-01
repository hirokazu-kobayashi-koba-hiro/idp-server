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


package org.idp.server.platform.condition;

import java.util.List;
import org.idp.server.platform.json.path.JsonPathWrapper;

public class ConditionEvaluator {

  public static boolean evaluate(
      List<ConditionDefinition> conditions, ConditionMatchMode matchMode, JsonPathWrapper json) {
    if (conditions == null || conditions.isEmpty()) {
      return true;
    }

    if (matchMode == ConditionMatchMode.ALL) {
      return conditions.stream().allMatch(c -> evaluateCondition(c, json));
    }

    return conditions.stream().anyMatch(c -> evaluateCondition(c, json));
  }

  private static boolean evaluateCondition(ConditionDefinition condition, JsonPathWrapper json) {
    Object actualValue = json.readRaw(condition.path());
    return ConditionOperationEvaluator.evaluate(
        actualValue, condition.operation(), condition.value());
  }
}

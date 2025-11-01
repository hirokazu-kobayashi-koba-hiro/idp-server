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

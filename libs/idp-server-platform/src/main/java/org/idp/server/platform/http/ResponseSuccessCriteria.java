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

package org.idp.server.platform.http;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.condition.ConditionOperationEvaluator;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.json.path.JsonPathWrapper;

/**
 * Criteria for determining success of HTTP response based on response body content.
 *
 * <p>This class enables detection of error conditions in HTTP 200 responses where the error is
 * indicated in the response body rather than the HTTP status code. This pattern is used by some
 * external APIs (e.g., Twilio Verify SNA).
 *
 * <p>Example configuration:
 *
 * <pre>{@code
 * {
 *   "conditions": [
 *     {"path": "$.status", "operation": "eq", "value": "success"},
 *     {"path": "$.error", "operation": "missing"}
 *   ],
 *   "match_mode": "all",
 *   "error_status_code": 400
 * }
 * }</pre>
 *
 * <p>The {@code error_status_code} field allows customizing the HTTP status code returned when
 * criteria evaluation fails. Common use cases:
 *
 * <ul>
 *   <li>400/422: Client validation errors from external API
 *   <li>401: Authentication failures
 *   <li>403: Permission denied
 *   <li>404: Resource not found
 *   <li>502: External service errors (default)
 *   <li>503: Service temporarily unavailable
 * </ul>
 */
public class ResponseSuccessCriteria implements JsonReadable {
  public List<ResponseCondition> conditions;
  public ConditionMatchMode matchMode;
  public Integer errorStatusCode;

  public ResponseSuccessCriteria() {}

  public ResponseSuccessCriteria(List<ResponseCondition> conditions, ConditionMatchMode matchMode) {
    this.conditions = conditions;
    this.matchMode = matchMode;
  }

  /**
   * Creates empty criteria that always evaluates to true (default HTTP status-only behavior).
   *
   * @return ResponseSuccessCriteria with no conditions
   */
  public static ResponseSuccessCriteria empty() {
    return new ResponseSuccessCriteria(Collections.emptyList(), ConditionMatchMode.ALL);
  }

  /**
   * Evaluates whether the response body meets the success criteria.
   *
   * @param responseBody JSON response body wrapped in JsonPathWrapper
   * @return true if criteria are met (or no criteria configured), false otherwise
   */
  public boolean evaluate(JsonPathWrapper responseBody) {
    if (conditions == null || conditions.isEmpty()) {
      return true; // No criteria = default behavior (HTTP status only)
    }

    if (matchMode == ConditionMatchMode.ALL) {
      return conditions.stream().allMatch(c -> evaluateCondition(c, responseBody));
    } else {
      return conditions.stream().anyMatch(c -> evaluateCondition(c, responseBody));
    }
  }

  private boolean evaluateCondition(ResponseCondition condition, JsonPathWrapper json) {
    Object actualValue = json.readRaw(condition.path());
    return ConditionOperationEvaluator.evaluate(
        actualValue, condition.operation(), condition.value());
  }

  public List<ResponseCondition> conditions() {
    return conditions;
  }

  public ConditionMatchMode matchMode() {
    return matchMode;
  }

  /**
   * Returns the HTTP status code to use when criteria evaluation fails.
   *
   * @return configured error status code, or 502 (Bad Gateway) as default
   */
  public int errorStatusCode() {
    return errorStatusCode != null ? errorStatusCode : 502;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (conditions != null) {
      map.put("conditions", conditions.stream().map(ResponseCondition::toMap).toList());
    }
    if (matchMode != null) {
      map.put("match_mode", matchMode.name());
    }
    if (errorStatusCode != null) {
      map.put("error_status_code", errorStatusCode);
    }
    return map;
  }

  public boolean exists() {
    return conditions != null && !conditions.isEmpty();
  }
}

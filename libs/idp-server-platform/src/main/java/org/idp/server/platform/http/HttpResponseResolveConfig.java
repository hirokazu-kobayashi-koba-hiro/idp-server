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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.condition.ConditionDefinition;
import org.idp.server.platform.condition.ConditionMatchMode;
import org.idp.server.platform.json.JsonReadable;

/**
 * Configuration for resolving HTTP responses into application-level status codes.
 *
 * <p>This class allows flexible mapping of HTTP responses to internal status codes based on:
 *
 * <ul>
 *   <li>HTTP status code conditions
 *   <li>Response body conditions using JSONPath
 *   <li>Logical operators (ALL/ANY) for combining conditions
 * </ul>
 *
 * <p>Configuration example:
 *
 * <pre>{@code
 * {
 *   "conditions": [
 *     {
 *       "path": "$.httpStatusCode",
 *       "operation": "in",
 *       "value": [200, 201]
 *     },
 *     {
 *       "path": "$.status",
 *       "operation": "eq",
 *       "value": "approved"
 *     }
 *   ],
 *   "match_mode": "all",
 *   "mapped_status_code": 200
 * }
 * }</pre>
 */
public class HttpResponseResolveConfig implements JsonReadable {
  private List<ConditionDefinition> conditions;
  private String matchMode;
  private int mappedStatusCode;

  public HttpResponseResolveConfig() {
    this.conditions = new ArrayList<>();
    this.matchMode = ConditionMatchMode.ALL.name().toLowerCase();
  }

  public HttpResponseResolveConfig(
      List<ConditionDefinition> conditions, String matchMode, int mappedStatusCode) {
    this.conditions = conditions;
    this.matchMode = matchMode;
    this.mappedStatusCode = mappedStatusCode;
  }

  public List<ConditionDefinition> conditions() {
    return conditions;
  }

  public ConditionMatchMode matchMode() {
    return ConditionMatchMode.of(matchMode);
  }

  public int mappedStatusCode() {
    return mappedStatusCode;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    List<Map<String, Object>> conditionMaps = new ArrayList<>();
    for (ConditionDefinition condition : conditions) {
      conditionMaps.add(condition.toMap());
    }
    map.put("conditions", conditionMaps);
    map.put("match_mode", matchMode);
    map.put("mapped_status_code", mappedStatusCode);

    return map;
  }
}

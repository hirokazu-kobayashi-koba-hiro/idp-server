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
import org.idp.server.platform.json.JsonReadable;

/**
 * Container for multiple HttpResponseResolveConfig instances.
 *
 * <p>Configurations are evaluated in order, and the first matching configuration is used.
 *
 * <p>Configuration example:
 *
 * <pre>{@code
 * {
 *   "configs": [
 *     {
 *       "conditions": [
 *         {"path": "$.httpStatusCode", "operation": "in", "value": [200, 201]},
 *         {"path": "$.status", "operation": "eq", "value": "approved"}
 *       ],
 *       "match_mode": "all",
 *       "mapped_status_code": 200,
 *       "data_json_path": "$.result"
 *     },
 *     {
 *       "conditions": [
 *         {"path": "$.httpStatusCode", "operation": "eq", "value": 503}
 *       ],
 *       "match_mode": "all",
 *       "mapped_status_code": 503,
 *       "error_message_json_path": "$.message"
 *     }
 *   ]
 * }
 * }</pre>
 */
public class HttpResponseResolveConfigs implements JsonReadable {
  private List<HttpResponseResolveConfig> configs;

  public HttpResponseResolveConfigs() {
    this.configs = new ArrayList<>();
  }

  public HttpResponseResolveConfigs(List<HttpResponseResolveConfig> configs) {
    this.configs = configs;
  }

  public List<HttpResponseResolveConfig> configs() {
    return configs;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    List<Map<String, Object>> configMaps = new ArrayList<>();
    for (HttpResponseResolveConfig config : configs) {
      configMaps.add(config.toMap());
    }
    map.put("configs", configMaps);
    return map;
  }

  public boolean isEmpty() {
    return configs == null || configs.isEmpty();
  }
}

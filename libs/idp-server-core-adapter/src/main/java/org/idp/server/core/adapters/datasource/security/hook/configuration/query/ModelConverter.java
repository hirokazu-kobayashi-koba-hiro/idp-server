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


package org.idp.server.core.adapters.datasource.security.hook.configuration.query;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.platform.security.hook.SecurityEventHookConfiguration;

class ModelConverter {

  public static SecurityEventHookConfiguration convert(Map<String, String> result) {
    String id = result.get("id");
    String type = result.get("type");
    JsonNodeWrapper triggersNode = JsonNodeWrapper.fromString(result.get("triggers"));
    List<String> triggers = triggersNode.toList();
    JsonNodeWrapper payloadNode = JsonNodeWrapper.fromString(result.get("payload"));
    Map<String, Object> payload = payloadNode.toMap();
    int executionOrder = Integer.parseInt(result.get("execution_order"));
    boolean enabled = Boolean.parseBoolean(result.get("enabled"));

    return new SecurityEventHookConfiguration(id, type, triggers, executionOrder, enabled, payload);
  }
}

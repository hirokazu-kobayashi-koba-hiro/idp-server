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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;

public class HttpRequestDynamicMapper {

  List<MappingRule> mappingRules;
  Map<String, Object> payload;

  public HttpRequestDynamicMapper(
      HttpRequestMappingRules httpRequestMappingRules,
      Map<String, String> headers,
      Map<String, Object> body) {
    this.mappingRules = httpRequestMappingRules.toList();
    Map<String, Object> payload = new HashMap<>();
    payload.put("header", headers);
    payload.put("body", body);
    this.payload = payload;
  }

  public Map<String, String> toHeaders() {
    return executeAndConvertString();
  }

  public Map<String, Object> toBody() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(payload);
    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
    return MappingRuleObjectMapper.execute(mappingRules, jsonPathWrapper);
  }

  public Map<String, String> toQueryParams() {
    return executeAndConvertString();
  }

  private Map<String, String> executeAndConvertString() {
    Map<String, String> resolvedHeaders = new HashMap<>();

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(payload);
    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> executed = MappingRuleObjectMapper.execute(mappingRules, jsonPathWrapper);

    for (Map.Entry<String, Object> entry : executed.entrySet()) {
      resolvedHeaders.put(entry.getKey(), entry.getValue().toString());
    }

    return resolvedHeaders;
  }
}

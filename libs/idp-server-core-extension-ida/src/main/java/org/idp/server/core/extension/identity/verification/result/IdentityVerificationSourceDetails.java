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

package org.idp.server.core.extension.identity.verification.result;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;

public class IdentityVerificationSourceDetails {

  JsonNodeWrapper json;

  public IdentityVerificationSourceDetails() {
    this.json = JsonNodeWrapper.empty();
  }

  public IdentityVerificationSourceDetails(JsonNodeWrapper json) {
    this.json = json;
  }

  public static IdentityVerificationSourceDetails fromJson(String json) {
    return new IdentityVerificationSourceDetails(JsonNodeWrapper.fromString(json));
  }

  public static IdentityVerificationSourceDetails fromMap(Map<String, Object> json) {
    return new IdentityVerificationSourceDetails(JsonNodeWrapper.fromMap(json));
  }

  public static IdentityVerificationSourceDetails create(
      Map<String, Object> context, List<MappingRule> mappingRules) {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(context);
    JsonPathWrapper jsonPath = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> mappingResult = MappingRuleObjectMapper.execute(mappingRules, jsonPath);

    return new IdentityVerificationSourceDetails(JsonNodeWrapper.fromMap(mappingResult));
  }

  public String getValueOrEmptyAsString(String fieldName) {
    return json.getValueOrEmptyAsString(fieldName);
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }

  public String toJson() {
    return json.toJson();
  }
}

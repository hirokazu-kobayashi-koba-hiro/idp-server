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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationApplicationContext;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationApplicationRequest;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;

public class IdentityVerificationApplicationDetails {

  JsonNodeWrapper json;

  public IdentityVerificationApplicationDetails() {
    this.json = JsonNodeWrapper.empty();
  }

  public IdentityVerificationApplicationDetails(JsonNodeWrapper json) {
    this.json = json;
  }

  public static IdentityVerificationApplicationDetails create(
      IdentityVerificationApplicationContext applicationContext, List<MappingRule> mappingRules) {

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(applicationContext.toMap());
    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> mappingResult =
        MappingRuleObjectMapper.execute(mappingRules, jsonPathWrapper);

    return new IdentityVerificationApplicationDetails(JsonNodeWrapper.fromMap(mappingResult));
  }

  public IdentityVerificationApplicationDetails merge(
      IdentityVerificationApplicationRequest request,
      IdentityVerificationProcessConfiguration processConfig) {
    JsonSchemaDefinition jsonSchemaDefinition = processConfig.requestSchemaAsDefinition();
    Map<String, Object> mappingResult =
        IdentityVerificationMapper.mapping(request.toMap(), jsonSchemaDefinition);
    Map<String, Object> mergedResult = new HashMap<>(json.toMap());
    ;
    mergedResult.putAll(mappingResult);

    return new IdentityVerificationApplicationDetails(JsonNodeWrapper.fromMap(mergedResult));
  }

  public String getValueOrEmptyAsString(String fieldName) {
    return json.getValueOrEmptyAsString(fieldName);
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }
}

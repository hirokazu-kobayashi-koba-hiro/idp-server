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

package org.idp.server.core.extension.identity.verification.delegation;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationMapper;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;

public class ExternalWorkflowApplicationDetails {

  JsonNodeWrapper json;

  public ExternalWorkflowApplicationDetails() {
    this.json = JsonNodeWrapper.empty();
  }

  public ExternalWorkflowApplicationDetails(JsonNodeWrapper json) {
    this.json = json;
  }

  public static ExternalWorkflowApplicationDetails create(
      JsonNodeWrapper body, IdentityVerificationProcessConfiguration processConfig) {
    JsonSchemaDefinition jsonSchemaDefinition =
        processConfig.responseValidationSchemaAsDefinition();
    Map<String, Object> mappingResult =
        IdentityVerificationMapper.mapping(body.toMap(), jsonSchemaDefinition);
    return new ExternalWorkflowApplicationDetails(JsonNodeWrapper.fromObject(mappingResult));
  }

  public ExternalWorkflowApplicationDetails merge(
      JsonNodeWrapper body, IdentityVerificationProcessConfiguration processConfig) {
    JsonSchemaDefinition jsonSchemaDefinition =
        processConfig.responseValidationSchemaAsDefinition();
    Map<String, Object> mappingResult =
        IdentityVerificationMapper.mapping(body.toMap(), jsonSchemaDefinition);
    Map<String, Object> merged = new HashMap<>(json.toMap());
    merged.putAll(mappingResult);
    return new ExternalWorkflowApplicationDetails(JsonNodeWrapper.fromObject(merged));
  }

  public String getValueOrEmptyAsString(String fieldName) {
    return json.getValueOrEmptyAsString(fieldName);
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }

  public boolean exists() {
    return json != null && json.exists();
  }
}

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

package org.idp.server.core.extension.identity.verification.application;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;

public class IdentityVerificationExaminationResult {
  JsonNodeWrapper json;

  public IdentityVerificationExaminationResult() {
    this.json = JsonNodeWrapper.empty();
  }

  public IdentityVerificationExaminationResult(JsonNodeWrapper json) {
    this.json = json;
  }

  public static IdentityVerificationExaminationResult create(
      IdentityVerificationRequest request, IdentityVerificationProcessConfiguration processConfig) {

    JsonSchemaDefinition jsonSchemaDefinition = processConfig.requestValidationSchemaAsDefinition();
    Map<String, Object> mappingResult =
        IdentityVerificationMapper.mapping(request.toMap(), jsonSchemaDefinition);

    return new IdentityVerificationExaminationResult(JsonNodeWrapper.fromMap(mappingResult));
  }

  public IdentityVerificationExaminationResult merge(
      IdentityVerificationRequest request, IdentityVerificationProcessConfiguration processConfig) {
    JsonSchemaDefinition jsonSchemaDefinition = processConfig.requestValidationSchemaAsDefinition();
    Map<String, Object> mappingResult =
        IdentityVerificationMapper.mapping(request.toMap(), jsonSchemaDefinition);
    Map<String, Object> mergedResult = new HashMap<>(json.toMap());
    ;
    mergedResult.putAll(mappingResult);

    return new IdentityVerificationExaminationResult(JsonNodeWrapper.fromMap(mergedResult));
  }

  public String getValueOrEmptyAsString(String fieldName) {
    return json.getValueOrEmptyAsString(fieldName);
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }
}

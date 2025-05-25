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

package org.idp.server.core.extension.identity.verification.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;
import org.idp.server.platform.json.schema.JsonSchemaProperty;

public class IdentityVerificationDynamicResponseMapper {

  public static Map<String, Object> buildDynamicResponse(
      IdentityVerificationApplication application,
      JsonNodeWrapper externalResponse,
      IdentityVerificationProcess process,
      IdentityVerificationConfiguration verificationConfiguration) {

    Map<String, Object> userResponse = new HashMap<>();
    userResponse.put("id", application.identifier().value());
    userResponse.put(
        "external_workflow_application_id", application.externalApplicationId().value());
    userResponse.put("status", application.status().value());

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(process);
    JsonSchemaDefinition responseSchemaDefinition =
        processConfig.responseValidationSchemaAsDefinition();
    Map<String, JsonSchemaProperty> properties = responseSchemaDefinition.getProperties();

    for (Map.Entry<String, JsonSchemaProperty> entry : properties.entrySet()) {
      String field = entry.getKey();
      JsonSchemaProperty property = entry.getValue();

      if (property.shouldRespond() && externalResponse.contains(field)) {
        userResponse.put(field, externalResponse.getValue(field));
      }
    }
    return userResponse;
  }
}

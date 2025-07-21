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
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationApplicationContext;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationResponseConfig;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;

public class IdentityVerificationDynamicResponseMapper {

  public static Map<String, Object> buildDynamicResponse(
      IdentityVerificationApplication application,
      IdentityVerificationApplicationContext applicationContext,
      IdentityVerificationResponseConfig responseConfig) {

    Map<String, Object> response = new HashMap<>();
    response.put("id", application.identifier().value());
    response.put("status", application.status().value());

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(applicationContext.toMap());
    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> result =
        MappingRuleObjectMapper.execute(responseConfig.getBodyMappingRules(), jsonPathWrapper);

    response.putAll(result);

    return response;
  }
}

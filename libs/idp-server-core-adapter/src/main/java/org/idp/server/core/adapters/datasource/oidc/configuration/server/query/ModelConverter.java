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


package org.idp.server.core.adapters.datasource.oidc.configuration.server.query;

import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;

class ModelConverter {
  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static AuthorizationServerConfiguration convert(Map<String, String> stringMap) {
    return jsonConverter.read(stringMap.get("payload"), AuthorizationServerConfiguration.class);
  }
}

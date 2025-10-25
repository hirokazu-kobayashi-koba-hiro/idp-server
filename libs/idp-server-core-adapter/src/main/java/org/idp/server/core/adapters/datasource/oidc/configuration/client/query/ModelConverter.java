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

package org.idp.server.core.adapters.datasource.oidc.configuration.client.query;

import java.util.Map;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.json.JsonConverter;

class ModelConverter {
  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static ClientConfiguration convert(Map<String, String> stringMap) {
    ClientConfiguration clientConfiguration =
        jsonConverter.read(stringMap.get("payload"), ClientConfiguration.class);

    if (stringMap.containsKey("created_at")) {
      clientConfiguration.setCreatedAt(LocalDateTimeParser.parse(stringMap.get("created_at")));
    }

    if (stringMap.containsKey("updated_at")) {
      clientConfiguration.setUpdatedAt(LocalDateTimeParser.parse(stringMap.get("updated_at")));
    }

    return clientConfiguration;
  }
}

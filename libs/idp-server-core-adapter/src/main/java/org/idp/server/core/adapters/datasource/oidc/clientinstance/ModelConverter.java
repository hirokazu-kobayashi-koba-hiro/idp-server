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

package org.idp.server.core.adapters.datasource.oidc.clientinstance;

import java.util.Map;
import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;

class ModelConverter {
  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static ClientInstance convert(Map<String, String> stringMap) {
    return new ClientInstance(
        stringMap.get("id"),
        stringMap.get("tenant_id"),
        stringMap.get("client_id"),
        toMap(stringMap.get("instance_key")),
        stringMap.get("status"),
        toMap(stringMap.get("attestation_evidence")),
        stringMap.get("device_id"),
        parseOrNull(stringMap.get("created_at")),
        parseOrNull(stringMap.get("updated_at")),
        parseOrNull(stringMap.get("expires_at")),
        parseOrNull(stringMap.get("revoked_at")));
  }

  private static Map<String, Object> toMap(String json) {
    if (json == null || json.isEmpty()) {
      return Map.of();
    }
    JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(json);
    return jsonNodeWrapper.toMap();
  }

  private static java.time.LocalDateTime parseOrNull(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    return LocalDateTimeParser.parse(value);
  }
}

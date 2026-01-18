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

package org.idp.server.core.adapters.datasource.identity.device.credential;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.core.openid.identity.device.credential.DeviceCredential;
import org.idp.server.core.openid.identity.device.credential.DeviceCredentialIdentifier;
import org.idp.server.core.openid.identity.device.credential.DeviceCredentialType;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.json.JsonConverter;

public class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @SuppressWarnings("unchecked")
  public static DeviceCredential convert(Map<String, String> result) {
    String id = result.get("id");
    String credentialType = result.get("credential_type");
    String typeSpecificDataJson = result.get("type_specific_data");

    DeviceCredentialIdentifier identifier = new DeviceCredentialIdentifier(id);
    DeviceCredentialType type = DeviceCredentialType.valueOf(credentialType);

    Map<String, Object> typeSpecificData =
        (typeSpecificDataJson != null && !typeSpecificDataJson.isEmpty())
            ? jsonConverter.read(typeSpecificDataJson, Map.class)
            : Map.of();

    return new DeviceCredential(
        identifier,
        type,
        typeSpecificData,
        parseDateTime(result.get("created_at")),
        parseDateTime(result.get("expires_at")),
        parseDateTime(result.get("revoked_at")));
  }

  private static LocalDateTime parseDateTime(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    return LocalDateTimeParser.parse(value);
  }
}

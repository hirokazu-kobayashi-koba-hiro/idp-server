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
    String createdAtStr = result.get("created_at");
    String expiresAtStr = result.get("expires_at");
    String revokedAtStr = result.get("revoked_at");

    DeviceCredentialIdentifier identifier = new DeviceCredentialIdentifier(id);
    DeviceCredentialType type = DeviceCredentialType.valueOf(credentialType);

    // Parse type_specific_data JSONB
    String secretValue = null;
    String jwks = null;
    String algorithm = null;
    if (typeSpecificDataJson != null && !typeSpecificDataJson.isEmpty()) {
      Map<String, Object> typeSpecificData = jsonConverter.read(typeSpecificDataJson, Map.class);
      secretValue = (String) typeSpecificData.get("secret_value");
      Object jwksObj = typeSpecificData.get("jwks");
      if (jwksObj != null) {
        if (jwksObj instanceof String) {
          jwks = (String) jwksObj;
        } else {
          jwks = jsonConverter.write(jwksObj);
        }
      }
      algorithm = (String) typeSpecificData.get("algorithm");
    }

    LocalDateTime createdAt = null;
    if (createdAtStr != null && !createdAtStr.isEmpty()) {
      createdAt = LocalDateTimeParser.parse(createdAtStr);
    }

    LocalDateTime expiresAt = null;
    if (expiresAtStr != null && !expiresAtStr.isEmpty()) {
      expiresAt = LocalDateTimeParser.parse(expiresAtStr);
    }

    LocalDateTime revokedAt = null;
    if (revokedAtStr != null && !revokedAtStr.isEmpty()) {
      revokedAt = LocalDateTimeParser.parse(revokedAtStr);
    }

    return new DeviceCredential(
        identifier, type, secretValue, jwks, algorithm, createdAt, expiresAt, revokedAt);
  }
}

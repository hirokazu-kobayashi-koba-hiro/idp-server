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

package org.idp.server.authenticators.webauthn4j.datasource.credential;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.authenticators.webauthn4j.WebAuthn4jCredential;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;

class ModelConverter {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(ModelConverter.class);

  static WebAuthn4jCredential convert(Map<String, Object> result) {

    String id = (String) result.get("id");
    String userId = (String) result.get("user_id");
    String username = (String) result.get("username");
    String userDisplayName = (String) result.get("user_display_name");
    String rpId = (String) result.get("rp_id");
    String aaguid = (String) result.get("aaguid");
    String attestedCredentialData = (String) result.get("attested_credential_data");
    Integer signatureAlgorithm = (Integer) result.get("signature_algorithm");
    Long signCount = (Long) result.get("sign_count");
    Boolean rk = (Boolean) result.get("rk");

    // WebAuthn Level 3: Backup Flags
    Boolean backupEligible = (Boolean) result.get("backup_eligible");
    Boolean backupState = (Boolean) result.get("backup_state");

    // JSON columns
    Map<String, Object> authenticator = parseJsonColumn(result.get("authenticator"));
    Map<String, Object> attestation = parseJsonColumn(result.get("attestation"));
    Map<String, Object> extensions = parseJsonColumn(result.get("extensions"));
    Map<String, Object> device = parseJsonColumn(result.get("device"));
    Map<String, Object> metadata = parseJsonColumn(result.get("metadata"));

    // Convert LocalDateTime to epoch milliseconds
    Long createdAt = convertToEpochMillis((LocalDateTime) result.get("created_at"));
    Long updatedAt = convertToEpochMillis((LocalDateTime) result.get("updated_at"));
    Long authenticatedAt = convertToEpochMillis((LocalDateTime) result.get("authenticated_at"));

    return new WebAuthn4jCredential(
        id,
        userId,
        username,
        userDisplayName,
        rpId,
        aaguid,
        attestedCredentialData,
        signatureAlgorithm,
        signCount,
        rk,
        backupEligible,
        backupState,
        authenticator,
        attestation,
        extensions,
        device,
        metadata,
        createdAt,
        updatedAt,
        authenticatedAt);
  }

  private static Long convertToEpochMillis(LocalDateTime dateTime) {
    if (dateTime == null) {
      return null;
    }
    return SystemDateTime.toEpochMilli(dateTime);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parseJsonColumn(Object value) {
    if (value == null) {
      return new java.util.HashMap<>();
    }

    log.debug("parseJsonColumn: type={}, value={}", value.getClass().getName(), value);

    if (value instanceof Map) {
      return (Map<String, Object>) value;
    }

    if (value instanceof String) {
      String jsonString = (String) value;
      if (jsonString.isEmpty() || jsonString.equals("null")) {
        return new java.util.HashMap<>();
      }
      try {
        JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
        return jsonConverter.read(jsonString, Map.class);
      } catch (Exception e) {
        log.warn("parseJsonColumn: failed to parse JSON string: {}", e.getMessage());
        return new java.util.HashMap<>();
      }
    }

    // Handle PostgreSQL PGobject for JSONB columns
    String className = value.getClass().getName();
    if (className.contains("PGobject") || className.contains("PgArray")) {
      String stringValue = value.toString();
      if (stringValue != null && !stringValue.isEmpty() && !stringValue.equals("null")) {
        try {
          JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
          return jsonConverter.read(stringValue, Map.class);
        } catch (Exception e) {
          log.warn("parseJsonColumn: failed to parse PGobject: {}", e.getMessage());
        }
      }
    }

    log.warn("parseJsonColumn: unhandled type {}", className);
    return new java.util.HashMap<>();
  }
}

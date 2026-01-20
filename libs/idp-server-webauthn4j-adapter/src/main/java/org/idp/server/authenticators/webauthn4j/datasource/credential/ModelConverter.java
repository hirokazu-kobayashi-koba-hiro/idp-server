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
import java.util.List;
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
    String attestationType = (String) result.get("attestation_type");
    Boolean rk = (Boolean) result.get("rk");
    Integer credProtect = (Integer) result.get("cred_protect");

    // Handle transports - can be List<String> or JSON String depending on database
    List<String> transports = parseTransports(result.get("transports"));

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
        attestationType,
        rk,
        credProtect,
        transports,
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
  private static List<String> parseTransports(Object transportsValue) {
    if (transportsValue == null) {
      log.debug("parseTransports: value is null");
      return null;
    }

    log.debug(
        "parseTransports: type={}, value={}",
        transportsValue.getClass().getName(),
        transportsValue);

    if (transportsValue instanceof List) {
      List<String> result = (List<String>) transportsValue;
      log.debug("parseTransports: parsed as List, size={}", result.size());
      return result;
    }

    if (transportsValue instanceof String) {
      String jsonString = (String) transportsValue;
      if (jsonString.isEmpty() || jsonString.equals("null")) {
        log.debug("parseTransports: empty or null string");
        return null;
      }
      try {
        JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
        List<String> result = jsonConverter.read(jsonString, List.class);
        log.debug("parseTransports: parsed JSON string, size={}", result.size());
        return result;
      } catch (Exception e) {
        log.warn("parseTransports: failed to parse JSON string: {}", e.getMessage());
        return null;
      }
    }

    // Handle PostgreSQL PGobject for JSONB columns
    String className = transportsValue.getClass().getName();
    if (className.contains("PGobject") || className.contains("PgArray")) {
      String stringValue = transportsValue.toString();
      log.debug("parseTransports: PGobject detected, stringValue={}", stringValue);
      if (stringValue != null && !stringValue.isEmpty() && !stringValue.equals("null")) {
        try {
          JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
          List<String> result = jsonConverter.read(stringValue, List.class);
          log.debug("parseTransports: parsed PGobject, size={}", result.size());
          return result;
        } catch (Exception e) {
          log.warn("parseTransports: failed to parse PGobject: {}", e.getMessage());
        }
      }
    }

    log.warn("parseTransports: unhandled type {}", className);
    return null;
  }
}

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

package org.idp.server.platform.security.log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.event.SecurityEventType;

public class StructuredJsonLogFormatter implements SecurityEventLogFormatter {

  @Override
  public String format(SecurityEvent securityEvent, SecurityEventLogConfiguration config) {
    return formatWithStage(securityEvent, null, config);
  }

  @Override
  public String formatWithStage(
      SecurityEvent securityEvent, String stage, SecurityEventLogConfiguration config) {
    return formatWithStage(securityEvent, stage, config, Map.of());
  }

  @Override
  public String formatWithStage(
      SecurityEvent securityEvent,
      String stage,
      SecurityEventLogConfiguration config,
      Map<String, Object> additionalFields) {
    Map<String, Object> jsonMap = new HashMap<>();

    jsonMap.put("event_id", securityEvent.identifier().value());
    jsonMap.put("event_type", securityEvent.type().value());
    jsonMap.put("timestamp", securityEvent.createdAt().value().toString());

    if (stage != null) {
      jsonMap.put("stage", stage);
    }

    jsonMap.put("tenant_id", securityEvent.tenantIdentifierValue());

    if (config.includeUserId() && securityEvent.hasUser()) {
      jsonMap.put("user_id", securityEvent.userSub());
    }

    if (config.includeUserExSub() && securityEvent.hasUser() && securityEvent.userExSub() != null) {
      jsonMap.put("user_ex_sub", securityEvent.userExSub());
    }

    if (config.includeClientId()) {
      jsonMap.put("client_id", securityEvent.clientIdentifierValue());
    }

    if (config.includeIpAddress() && securityEvent.ipAddressValue() != null) {
      jsonMap.put("ip_address", securityEvent.ipAddressValue());
    }

    if (config.includeUserAgent() && securityEvent.userAgentValue() != null) {
      jsonMap.put("user_agent", securityEvent.userAgentValue());
    }

    if (config.includeEventDetail()) {
      Map<String, Object> eventDetail =
          scrubSensitiveData(securityEvent.detail().toMap(), config.getDetailScrubKeys());

      jsonMap.put("event_detail", eventDetail);
    }

    List<String> tags = new ArrayList<>();
    tags.add(getEventCategory(securityEvent.type()));
    if (securityEvent.type().value().endsWith("_failure")) {
      tags.add("failure");
    }
    if (securityEvent.type().value().endsWith("_success")) {
      tags.add("success");
    }
    jsonMap.put("tags", tags);

    jsonMap.putAll(additionalFields);

    try {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromMap(jsonMap);
      return wrapper.toString();
    } catch (Exception e) {
      return String.format(
          "event_type=%s tenant=%s user=%s",
          securityEvent.type().value(),
          securityEvent.tenantIdentifierValue(),
          securityEvent.userSub());
    }
  }

  private String getEventCategory(SecurityEventType eventType) {
    String type = eventType.value();
    if (type.startsWith("password_")) return "authentication";
    if (type.startsWith("fido_uaf_") || type.startsWith("webauthn_")) return "mfa";
    if (type.startsWith("oauth_")) return "oauth";
    if (type.startsWith("federation_")) return "federation";
    if (type.startsWith("user_")) return "user_management";
    if (type.startsWith("client_")) return "client_management";
    if (type.startsWith("email_")) return "email";
    if (type.startsWith("sms_")) return "sms";
    return "other";
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> scrubSensitiveData(Map<String, Object> data, List<String> scrubKeys) {
    Map<String, Object> scrubbedData = new HashMap<>();

    for (Map.Entry<String, Object> entry : data.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if (shouldScrubKey(key, scrubKeys)) {
        scrubbedData.put(key, "[SCRUBBED]");
      } else if (value instanceof Map) {
        // Recursively scrub nested maps
        scrubbedData.put(key, scrubSensitiveData((Map<String, Object>) value, scrubKeys));
      } else if (value instanceof List) {
        // Scrub lists that might contain maps
        scrubbedData.put(key, scrubSensitiveDataInList((List<Object>) value, scrubKeys));
      } else {
        scrubbedData.put(key, value);
      }
    }

    return scrubbedData;
  }

  @SuppressWarnings("unchecked")
  private List<Object> scrubSensitiveDataInList(List<Object> list, List<String> scrubKeys) {
    List<Object> scrubbedList = new ArrayList<>();

    for (Object item : list) {
      if (item instanceof Map) {
        scrubbedList.add(scrubSensitiveData((Map<String, Object>) item, scrubKeys));
      } else if (item instanceof List) {
        scrubbedList.add(scrubSensitiveDataInList((List<Object>) item, scrubKeys));
      } else {
        scrubbedList.add(item);
      }
    }

    return scrubbedList;
  }

  private boolean shouldScrubKey(String key, List<String> scrubKeys) {
    String lowerKey = key.toLowerCase().trim();
    return scrubKeys.stream()
        .map(s -> s.toLowerCase().trim())
        .anyMatch(scrubKey -> lowerKey.equals(scrubKey) || lowerKey.startsWith(scrubKey));
  }
}

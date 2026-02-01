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

package org.idp.server.platform.security.hook;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.uuid.UuidConvertable;

public class SecurityEventHookResultQueries implements UuidConvertable {
  Map<String, String> values;

  public SecurityEventHookResultQueries() {}

  public SecurityEventHookResultQueries(Map<String, String> values) {
    this.values = Objects.requireNonNullElseGet(values, HashMap::new);
  }

  public boolean hasFrom() {
    return values.containsKey("from");
  }

  public LocalDateTime from() {
    if (!hasFrom()) {
      throw new IllegalStateException(
          "from is not specified. Check hasFrom() before calling this method.");
    }
    return LocalDateTimeParser.parse(values.get("from"));
  }

  public boolean hasTo() {
    return values.containsKey("to");
  }

  public LocalDateTime to() {
    if (!hasTo()) {
      throw new IllegalStateException(
          "to is not specified. Check hasTo() before calling this method.");
    }
    return LocalDateTimeParser.parse(values.get("to"));
  }

  public boolean hasId() {
    return values.containsKey("id");
  }

  public String id() {
    return values.get("id");
  }

  public UUID idAsUuid() {
    return convertUuid(id());
  }

  public boolean hasSecurityEventId() {
    return values.containsKey("security_event_id");
  }

  public String securityEventId() {
    return values.get("security_event_id");
  }

  public UUID securityEventIdAsUuid() {
    return convertUuid(securityEventId());
  }

  public boolean hasEventType() {
    return values.containsKey("event_type");
  }

  public String eventType() {
    return values.get("event_type");
  }

  public List<String> eventTypes() {
    String value = values.get("event_type");
    if (value == null || value.isEmpty()) {
      return List.of();
    }
    return Arrays.asList(value.split(","));
  }

  public String hookType() {
    return values.get("hook_type");
  }

  public boolean hasHookType() {
    return values.containsKey("hook_type");
  }

  public boolean hasStatus() {
    return values.containsKey("status");
  }

  public String status() {
    return values.get("status");
  }

  public boolean hasUserId() {
    String value = values.get("user_id");
    return value != null && !value.isEmpty();
  }

  public String userId() {
    return values.get("user_id");
  }

  public boolean hasUserName() {
    String value = values.get("user_name");
    return value != null && !value.isEmpty();
  }

  public String userName() {
    return values.get("user_name");
  }

  /**
   * Returns the user name with LIKE special characters escaped for safe SQL LIKE search.
   *
   * <p>Escapes '%' and '_' characters which have special meaning in SQL LIKE patterns.
   *
   * @return escaped user name for LIKE search
   */
  public String userNameForLikeSearch() {
    String name = userName();
    if (name == null) {
      return null;
    }
    return name.replace("%", "\\%").replace("_", "\\_");
  }

  public boolean hasExternalUserId() {
    String value = values.get("external_user_id");
    return value != null && !value.isEmpty();
  }

  public String externalUserId() {
    return values.get("external_user_id");
  }

  public boolean hasSecurityEventPayload() {
    return !securityEventPayload().isEmpty();
  }

  public Map<String, String> securityEventPayload() {
    Map<String, String> details = new HashMap<>();
    for (Map.Entry<String, String> entry : values.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith("security_event_payload.")) {
        String value = entry.getValue();
        details.put(key.replace("security_event_payload.", ""), value);
      }
    }
    return details;
  }

  public boolean hasExecutionPayload() {
    return !securityEventPayload().isEmpty();
  }

  public Map<String, String> securityEventExecutionPayload() {
    Map<String, String> details = new HashMap<>();
    for (Map.Entry<String, String> entry : values.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith("execution_payload.")) {
        String value = entry.getValue();
        details.put(key.replace("execution_payload.", ""), value);
      }
    }
    return details;
  }

  public int limit() {
    if (!values.containsKey("limit")) {
      return 20;
    }
    return Integer.parseInt(values.get("limit"));
  }

  public int offset() {
    if (!values.containsKey("offset")) {
      return 0;
    }
    return Integer.parseInt(values.get("offset"));
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    for (Map.Entry<String, String> entry : values.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      map.put(key, value);
    }
    return map;
  }
}

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

package org.idp.server.platform.security;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.uuid.UuidConvertable;

public class SecurityEventQueries implements UuidConvertable {
  Map<String, String> values;

  public SecurityEventQueries() {}

  public SecurityEventQueries(Map<String, String> values) {
    this.values = Objects.requireNonNullElseGet(values, HashMap::new);
  }

  public boolean hasFrom() {
    return values.containsKey("from");
  }

  public boolean hasTo() {
    return values.containsKey("to");
  }

  public LocalDateTime from() {
    return LocalDateTimeParser.parse(values.get("from"));
  }

  public LocalDateTime to() {
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

  public boolean hasClientId() {
    String value = values.get("client_id");
    return value != null && !value.isEmpty();
  }

  public String clientId() {
    return values.get("client_id");
  }

  public String userId() {
    return values.get("user_id");
  }

  public UUID userIdAsUuid() {
    return convertUuid(userId());
  }

  public boolean hasUserId() {
    String value = values.get("user_id");
    return value != null && !value.isEmpty();
  }

  public String externalUserId() {
    return values.get("external_user_id");
  }

  public boolean hasExternalUserId() {
    String value = values.get("external_user_id");
    return value != null && !value.isEmpty();
  }

  public String userName() {
    return values.get("user_name");
  }

  public boolean hasUserName() {
    String value = values.get("user_name");
    return value != null && !value.isEmpty();
  }

  public boolean hasEventType() {
    String value = values.get("event_type");
    return value != null && !value.isEmpty();
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

  public boolean hasIpAddress() {
    String value = values.get("ip_address");
    return value != null && !value.isEmpty();
  }

  public String ipAddress() {
    return values.get("ip_address");
  }

  public boolean hasUserAgent() {
    String value = values.get("user_agent");
    return value != null && !value.isEmpty();
  }

  public String userAgent() {
    return values.get("user_agent");
  }

  public boolean hasDetails() {
    return !details().isEmpty();
  }

  public Map<String, String> details() {
    Map<String, String> details = new HashMap<>();
    for (Map.Entry<String, String> entry : values.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith("details.")) {
        String value = entry.getValue();
        details.put(key.replace("details.", ""), value);
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

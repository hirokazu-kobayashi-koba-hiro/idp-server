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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.uuid.UuidConvertable;

public class SecurityEventHookResultQueries implements UuidConvertable {
  Map<String, String> values;

  public SecurityEventHookResultQueries() {}

  public SecurityEventHookResultQueries(Map<String, String> values) {
    this.values = Objects.requireNonNullElseGet(values, HashMap::new);
  }

  public LocalDateTime from() {
    if (!values.containsKey("from")) {
      return SystemDateTime.now().minusDays(7);
    }
    return LocalDateTimeParser.parse(values.get("from"));
  }

  public LocalDateTime to() {
    if (!values.containsKey("to")) {
      return SystemDateTime.now().plusDays(1);
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

  public boolean hasEventType() {
    return values.containsKey("event_type");
  }

  public String eventType() {
    return values.get("event_type");
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
}

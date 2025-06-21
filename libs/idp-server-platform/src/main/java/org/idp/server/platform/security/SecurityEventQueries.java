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

import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.uuid.UuidConvertable;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class SecurityEventQueries implements UuidConvertable {
  Map<String, String> values;

  public SecurityEventQueries() {}

  public SecurityEventQueries(Map<String, String> values) {
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

  public boolean hasClientId() {
    return values.containsKey("client_id");
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
    return values.containsKey("user_id");
  }

  public String externalUserId() {
    return values.get("external_user_id");
  }

  public boolean hasExternalUserId() {
    return values.containsKey("external_user_id");
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
}

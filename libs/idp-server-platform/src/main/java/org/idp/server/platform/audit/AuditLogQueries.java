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

package org.idp.server.platform.audit;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.uuid.UuidConvertable;

public class AuditLogQueries implements UuidConvertable {
  Map<String, String> values;

  public AuditLogQueries() {}

  public AuditLogQueries(Map<String, String> values) {
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

  public boolean hasType() {
    return values.containsKey("type");
  }

  public String type() {
    return values.get("type");
  }

  public boolean hasDescription() {
    return values.containsKey("description");
  }

  public String description() {
    return values.get("description");
  }

  public boolean hasTargetResource() {
    return values.containsKey("target_resource");
  }

  public String targetResource() {
    return values.get("target_resource");
  }

  public boolean hasTargetAction() {
    return values.containsKey("target_action");
  }

  public String targetAction() {
    return values.get("target_action");
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

  public boolean hasAttributes() {
    return !attributes().isEmpty();
  }

  public Map<String, String> attributes() {
    Map<String, String> details = new HashMap<>();
    for (Map.Entry<String, String> entry : values.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith("attributes.")) {
        String value = entry.getValue();
        details.put(key.replace("attributes.", ""), value);
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

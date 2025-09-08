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

package org.idp.server.core.openid.federation;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.uuid.UuidConvertable;

public class FederationQueries implements UuidConvertable {
  Map<String, String> values;

  public FederationQueries() {}

  public FederationQueries(Map<String, String> values) {
    this.values = Objects.requireNonNullElseGet(values, HashMap::new);
  }

  public boolean hasFrom() {
    return values.containsKey("from");
  }

  public LocalDateTime from() {
    return LocalDateTimeParser.parse(values.get("from"));
  }

  public boolean hasTo() {
    return values.containsKey("to");
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

  public String type() {
    return values.get("type");
  }

  public boolean hasType() {
    return values.containsKey("type");
  }

  public String ssoProvider() {
    return values.get("sso_provider");
  }

  public boolean hasSsoProvider() {
    return values.containsKey("sso_provider");
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

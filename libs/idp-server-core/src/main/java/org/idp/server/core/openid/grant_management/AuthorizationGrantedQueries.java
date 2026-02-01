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

package org.idp.server.core.openid.grant_management;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.platform.date.LocalDateTimeParser;

/** Query parameters for authorization granted search operations. */
public class AuthorizationGrantedQueries {
  Map<String, String> values;

  public AuthorizationGrantedQueries() {
    this.values = new HashMap<>();
  }

  public AuthorizationGrantedQueries(Map<String, String> values) {
    this.values = Objects.requireNonNullElseGet(values, HashMap::new);
  }

  public boolean hasUserId() {
    String value = values.get("user_id");
    return value != null && !value.isEmpty();
  }

  public String userId() {
    return values.get("user_id");
  }

  public boolean hasClientId() {
    String value = values.get("client_id");
    return value != null && !value.isEmpty();
  }

  public String clientId() {
    return values.get("client_id");
  }

  public boolean hasFrom() {
    return values.containsKey("from") && values.get("from") != null;
  }

  public LocalDateTime from() {
    return LocalDateTimeParser.parse(values.get("from"));
  }

  public boolean hasTo() {
    return values.containsKey("to") && values.get("to") != null;
  }

  public LocalDateTime to() {
    return LocalDateTimeParser.parse(values.get("to"));
  }

  public int limit() {
    if (!values.containsKey("limit")) {
      return 20;
    }
    try {
      int limit = Integer.parseInt(values.get("limit"));
      return Math.min(limit, 1000);
    } catch (NumberFormatException e) {
      return 20;
    }
  }

  public int offset() {
    if (!values.containsKey("offset")) {
      return 0;
    }
    try {
      return Integer.parseInt(values.get("offset"));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  public Map<String, String> toMap() {
    return values;
  }
}

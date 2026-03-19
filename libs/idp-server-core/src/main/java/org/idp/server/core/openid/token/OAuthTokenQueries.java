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

package org.idp.server.core.openid.token;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import org.idp.server.platform.date.LocalDateTimeParser;

public class OAuthTokenQueries {

  private static final int MAX_LIMIT = 1000;

  private final Map<String, String> values;

  public OAuthTokenQueries(Map<String, String> values) {
    this.values = values;
  }

  public int limit() {
    try {
      int parsed = Integer.parseInt(values.getOrDefault("limit", "20"));
      return Math.min(Math.max(parsed, 1), MAX_LIMIT);
    } catch (NumberFormatException e) {
      return 20;
    }
  }

  public int offset() {
    try {
      return Math.max(Integer.parseInt(values.getOrDefault("offset", "0")), 0);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  public boolean hasUserId() {
    return values.containsKey("user_id")
        && Objects.nonNull(values.get("user_id"))
        && !values.get("user_id").isEmpty();
  }

  public String userId() {
    return values.get("user_id");
  }

  public boolean hasClientId() {
    return values.containsKey("client_id")
        && Objects.nonNull(values.get("client_id"))
        && !values.get("client_id").isEmpty();
  }

  public String clientId() {
    return values.get("client_id");
  }

  public boolean hasGrantType() {
    return values.containsKey("grant_type")
        && Objects.nonNull(values.get("grant_type"))
        && !values.get("grant_type").isEmpty();
  }

  public String grantType() {
    return values.get("grant_type");
  }

  public boolean hasFrom() {
    return values.containsKey("from")
        && Objects.nonNull(values.get("from"))
        && !values.get("from").isEmpty();
  }

  public LocalDateTime from() {
    return LocalDateTimeParser.parse(values.get("from"));
  }

  public boolean hasTo() {
    return values.containsKey("to")
        && Objects.nonNull(values.get("to"))
        && !values.get("to").isEmpty();
  }

  public LocalDateTime to() {
    return LocalDateTimeParser.parse(values.get("to"));
  }

  public boolean includeExpired() {
    String expired = values.getOrDefault("expired", "false");
    return Boolean.parseBoolean(expired);
  }
}

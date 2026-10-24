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

package org.idp.server.account_linking;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.idp.server.platform.date.LocalDateTimeParser;

/**
 * Filters for listing a tenant's linked external accounts, as the management API takes them.
 *
 * <p>{@code provider} with {@code limit=1} answers how many links a provider still has, through the
 * list's {@code total_count}: what an operator needs before removing or renaming the provider.
 */
public class LinkedExternalAccountQueries {

  static final int DEFAULT_LIMIT = 20;
  static final int MAX_LIMIT = 1000;

  Map<String, String> values;

  public LinkedExternalAccountQueries() {
    this.values = new HashMap<>();
  }

  public LinkedExternalAccountQueries(Map<String, String> values) {
    this.values = Objects.requireNonNullElseGet(values, HashMap::new);
  }

  public boolean hasProvider() {
    return hasValue("provider");
  }

  public String provider() {
    return values.get("provider");
  }

  public boolean hasUserId() {
    return hasValue("user_id");
  }

  public String userId() {
    return values.get("user_id");
  }

  /** Whether {@code user_id}, when given, is a UUID. Anything else can match no user. */
  public boolean isUserIdValid() {
    if (!hasUserId()) {
      return true;
    }
    try {
      UUID.fromString(userId());
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  public boolean hasFrom() {
    return hasValue("from");
  }

  public LocalDateTime from() {
    return LocalDateTimeParser.parse(values.get("from"));
  }

  public boolean hasTo() {
    return hasValue("to");
  }

  public LocalDateTime to() {
    return LocalDateTimeParser.parse(values.get("to"));
  }

  /** Between 1 and 1000; 20 when absent or not a positive number. */
  public int limit() {
    try {
      int limit = Integer.parseInt(values.getOrDefault("limit", ""));
      return limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    } catch (NumberFormatException exception) {
      return DEFAULT_LIMIT;
    }
  }

  /** 0 when absent or negative. */
  public int offset() {
    try {
      return Math.max(Integer.parseInt(values.getOrDefault("offset", "")), 0);
    } catch (NumberFormatException exception) {
      return 0;
    }
  }

  public Map<String, String> toMap() {
    return values;
  }

  private boolean hasValue(String key) {
    String value = values.get(key);
    return value != null && !value.isEmpty();
  }
}

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
package org.idp.server.platform.crypto.datakey;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Filters for listing data keys across tenants: by tenant, key encryption key, status and purpose,
 * a page at a time.
 *
 * <p>Filtering by key encryption key finds the tenants a missing or retired one leaves unable to
 * open their data.
 */
public class TenantDataKeyQueries {

  static final int DEFAULT_LIMIT = 20;
  static final int MAX_LIMIT = 1000;

  Map<String, String> values;

  public TenantDataKeyQueries() {
    this.values = new HashMap<>();
  }

  public TenantDataKeyQueries(Map<String, String> values) {
    this.values = Objects.requireNonNullElseGet(values, HashMap::new);
  }

  public boolean hasTenantId() {
    return hasValue("tenant_id");
  }

  public String tenantId() {
    return values.get("tenant_id");
  }

  /** Whether {@code tenant_id}, when given, is a UUID. */
  public boolean isTenantIdValid() {
    if (!hasTenantId()) {
      return true;
    }
    try {
      UUID.fromString(tenantId());
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  public boolean hasKekId() {
    return hasValue("kek_id");
  }

  public String kekId() {
    return values.get("kek_id");
  }

  public boolean hasStatus() {
    return hasValue("status");
  }

  public String status() {
    return values.get("status");
  }

  /** Whether {@code status}, when given, is one a data key can have. */
  public boolean isStatusValid() {
    if (!hasStatus()) {
      return true;
    }
    try {
      DataKeyStatus.of(status());
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  public boolean hasPurpose() {
    return hasValue("purpose");
  }

  public String purpose() {
    return values.get("purpose");
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

  private boolean hasValue(String key) {
    String value = values.get(key);
    return value != null && !value.isEmpty();
  }
}

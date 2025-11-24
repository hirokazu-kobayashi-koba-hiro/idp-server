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

package org.idp.server.platform.statistics;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class TenantStatisticsQueries {
  Map<String, String> values;

  public TenantStatisticsQueries() {}

  public TenantStatisticsQueries(Map<String, String> values) {
    this.values = Objects.requireNonNullElseGet(values, HashMap::new);
  }

  public boolean hasTenantId() {
    return values.containsKey("tenant_id");
  }

  public String tenantId() {
    return values.get("tenant_id");
  }

  public boolean hasFrom() {
    return values.containsKey("from");
  }

  public String from() {
    return values.get("from");
  }

  public LocalDate fromAsLocalDate() {
    return LocalDate.parse(values.get("from"));
  }

  public boolean hasTo() {
    return values.containsKey("to");
  }

  public String to() {
    return values.get("to");
  }

  public LocalDate toAsLocalDate() {
    return LocalDate.parse(values.get("to"));
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (hasTenantId()) {
      map.put("tenant_id", tenantId());
    }
    if (hasFrom()) {
      map.put("from", from());
    }
    if (hasTo()) {
      map.put("to", to());
    }
    return map;
  }
}

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
package org.idp.server.control_plane.management.tenant.io;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/** A tenant's data keys, optionally narrowed to one purpose or status. */
public record TenantDataKeyFindListRequest(
    TenantIdentifier tenantIdentifier, Map<String, String> queries)
    implements TenantManagementRequest {

  public TenantDataKeyFindListRequest {
    queries = Objects.requireNonNullElseGet(queries, HashMap::new);
  }

  public String purpose() {
    return valueOrNull("purpose");
  }

  public String status() {
    return valueOrNull("status");
  }

  @Override
  public Map<String, Object> toMap() {
    return new HashMap<>(queries);
  }

  @Override
  public boolean hasTenantIdentifier() {
    return true;
  }

  private String valueOrNull(String key) {
    String value = queries.get(key);
    return value == null || value.isEmpty() ? null : value;
  }
}

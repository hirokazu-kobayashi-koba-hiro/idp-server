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

package org.idp.server.platform.multi_tenancy.organization;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.uuid.UuidConvertable;

public class AssignedTenant implements UuidConvertable {
  String id;
  String name;
  String type;

  public AssignedTenant() {}

  public AssignedTenant(String id, String name, String type) {
    this.id = id;
    this.name = name;
    this.type = type;
  }

  public String id() {
    return id;
  }

  public UUID idAsUuid() {
    return convertUuid(id);
  }

  public String name() {
    return name;
  }

  public String type() {
    return type;
  }

  public TenantIdentifier tenantIdentifier() {
    return new TenantIdentifier(id);
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("name", name);
    map.put("type", type);

    return map;
  }
}

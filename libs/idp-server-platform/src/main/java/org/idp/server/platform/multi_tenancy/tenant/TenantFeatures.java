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

package org.idp.server.platform.multi_tenancy.tenant;

import java.util.HashMap;
import java.util.Map;

public class TenantFeatures {
  Map<String, TenantFeature> values;

  public TenantFeatures() {
    this.values = new HashMap<>();
  }

  public TenantFeatures(Map<String, TenantFeature> values) {
    this.values = values;
  }

  public TenantFeature get(String name) {
    return values.get(name);
  }

  public boolean containsKey(String name) {
    return values.containsKey(name);
  }

  public TenantFeatures updateWith(TenantFeatures other) {
    values.putAll(other.values);
    return new TenantFeatures(values);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    values.forEach((k, v) -> map.put(k, v.toMap()));
    return map;
  }
}

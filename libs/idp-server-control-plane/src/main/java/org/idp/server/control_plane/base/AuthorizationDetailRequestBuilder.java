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

package org.idp.server.control_plane.base;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuthorizationDetailRequestBuilder {
  Map<String, Object> detail;

  public AuthorizationDetailRequestBuilder() {
    this.detail = new HashMap<>();
  }

  public AuthorizationDetailRequestBuilder addType(String type) {
    detail.put("type", type);
    return this;
  }

  public AuthorizationDetailRequestBuilder addActions(List<String> actions) {
    detail.put("actions", actions);
    return this;
  }

  public AuthorizationDetailRequestBuilder addLocations(List<String> locations) {
    detail.put("locations", locations);
    return this;
  }

  public AuthorizationDetailRequestBuilder add(String key, Object value) {
    this.detail.put(key, value);
    return this;
  }

  public Map<String, Object> build() {
    return detail;
  }
}

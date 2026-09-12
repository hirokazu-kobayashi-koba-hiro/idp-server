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

package org.idp.server.core.openid.clientinstance.registration;

import java.util.HashMap;
import java.util.Map;

/** Request of the registration endpoint. */
public class ClientInstanceRegisterRequest {

  Map<String, Object> values;

  public ClientInstanceRegisterRequest(Map<String, Object> values) {
    this.values = values != null ? values : new HashMap<>();
  }

  public String challenge() {
    Object value = values.get("challenge");
    return value instanceof String stringValue && !stringValue.isEmpty() ? stringValue : null;
  }

  public Map<String, Object> instanceKey() {
    return mapValue("client_instance_public_key");
  }

  public Map<String, Object> platformEvidence() {
    return mapValue("platform_evidence");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> mapValue(String key) {
    Object value = values.get(key);
    return value instanceof Map ? (Map<String, Object>) value : Map.of();
  }
}

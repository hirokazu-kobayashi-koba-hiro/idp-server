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

package org.idp.server.core.oidc.authentication;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.platform.uuid.UuidConvertable;

public class AuthenticationTransactionQueries implements UuidConvertable {
  Map<String, String> values;

  public AuthenticationTransactionQueries() {}

  public AuthenticationTransactionQueries(Map<String, String> values) {
    this.values = values;
  }

  public boolean hasId() {
    return values.containsKey("id");
  }

  public String id() {
    return values.get("id");
  }

  public UUID idAsUuid() {
    return convertUuid(id());
  }

  public boolean hasFlow() {
    return values.containsKey("flow");
  }

  public String flow() {
    return values.get("flow");
  }

  public boolean hasClientId() {
    return values.containsKey("client_id");
  }

  public String clientId() {
    return values.get("client_id");
  }

  public String deviceId() {
    return values.get("device_id");
  }

  public boolean hasDeviceId() {
    return values.containsKey("device_id");
  }

  public boolean hasAttributes() {
    return !attributes().isEmpty();
  }

  public Map<String, String> attributes() {
    Map<String, String> attributes = new HashMap<>();
    for (Map.Entry<String, String> entry : values.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      attributes.put(key.replace("attributes.", ""), value);
    }
    return attributes;
  }
}

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

package org.idp.server.core.openid.identity.device;

import java.util.Map;

public class AuthenticationDeviceLogRequest {

  Map<String, Object> values;

  public AuthenticationDeviceLogRequest(Map<String, Object> values) {
    this.values = values != null ? values : Map.of();
  }

  public String deviceId() {
    return extractString("device_id");
  }

  public String userId() {
    return extractString("user_id");
  }

  public String clientId() {
    return extractString("client_id");
  }

  public String clientName() {
    return extractString("client_name");
  }

  public String clientIdOrDefault() {
    String clientId = clientId();
    return (clientId != null && !clientId.isEmpty()) ? clientId : "authentication-device";
  }

  public String clientNameOrDefault() {
    String clientName = clientName();
    return (clientName != null && !clientName.isEmpty()) ? clientName : "Authentication Device";
  }

  public boolean hasDeviceId() {
    String deviceId = deviceId();
    return deviceId != null && !deviceId.isEmpty();
  }

  public boolean hasUserId() {
    String userId = userId();
    return userId != null && !userId.isEmpty();
  }

  public Map<String, Object> toMap() {
    return values;
  }

  private String extractString(String key) {
    Object value = values.get(key);
    if (value instanceof String) {
      return (String) value;
    }
    return null;
  }
}

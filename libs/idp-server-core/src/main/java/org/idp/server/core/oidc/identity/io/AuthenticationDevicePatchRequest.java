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

package org.idp.server.core.oidc.identity.io;

import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.identity.device.AuthenticationDevice;
import org.idp.server.core.oidc.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.json.JsonNodeWrapper;

public class AuthenticationDevicePatchRequest {
  JsonNodeWrapper jsonNodeWrapper;

  public AuthenticationDevicePatchRequest() {
    this.jsonNodeWrapper = JsonNodeWrapper.empty();
  }

  public AuthenticationDevicePatchRequest(JsonNodeWrapper jsonNodeWrapper) {
    this.jsonNodeWrapper = jsonNodeWrapper;
  }

  public static AuthenticationDevicePatchRequest fromMap(Map<String, Object> map) {
    return new AuthenticationDevicePatchRequest(JsonNodeWrapper.fromMap(map));
  }

  public static AuthenticationDevicePatchRequest fromJson(String json) {
    return new AuthenticationDevicePatchRequest(JsonNodeWrapper.fromString(json));
  }

  public Map<String, Object> toMap() {
    return jsonNodeWrapper.toMap();
  }

  public JsonNodeWrapper jsonNodeWrapper() {
    return jsonNodeWrapper;
  }

  public boolean exists() {
    return jsonNodeWrapper != null && !jsonNodeWrapper.exists();
  }

  public AuthenticationDevice toAuthenticationDevice(AuthenticationDeviceIdentifier identifier) {
    String id = identifier.value();
    String appName = jsonNodeWrapper.getValueOrEmptyAsString("app_name");
    String platform = jsonNodeWrapper.getValueOrEmptyAsString("platform");
    String os = jsonNodeWrapper.getValueOrEmptyAsString("os");
    String model = jsonNodeWrapper.getValueOrEmptyAsString("model");
    String locale = jsonNodeWrapper.getValueOrEmptyAsString("locale");
    String notificationChannel = jsonNodeWrapper.getValueOrEmptyAsString("notification_channel");
    String notificationToken = jsonNodeWrapper.getValueOrEmptyAsString("notification_token");
    List<String> availableMethods = List.of();
    Integer priority = jsonNodeWrapper.getValueAsInteger("priority");
    return new AuthenticationDevice(
        id,
        appName,
        platform,
        os,
        model,
        locale,
        notificationChannel,
        notificationToken,
        availableMethods,
        priority);
  }
}

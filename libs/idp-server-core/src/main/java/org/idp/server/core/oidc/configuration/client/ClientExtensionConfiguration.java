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

package org.idp.server.core.oidc.configuration.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionType;
import org.idp.server.platform.json.JsonReadable;

public class ClientExtensionConfiguration implements JsonReadable {

  Long accessTokenDuration;
  Long refreshTokenDuration;
  boolean supportedJar = false;
  List<AvailableFederation> availableFederations;
  String defaultCibaAuthenticationInteractionType = "authentication-device-notification-no-action";

  public ClientExtensionConfiguration() {}

  public long accessTokenDuration() {
    return accessTokenDuration;
  }

  public boolean hasAccessTokenDuration() {
    return accessTokenDuration != null && accessTokenDuration > 0;
  }

  public long refreshTokenDuration() {
    return refreshTokenDuration;
  }

  public boolean hasRefreshTokenDuration() {
    return refreshTokenDuration != null && refreshTokenDuration > 0;
  }

  public boolean isSupportedJar() {
    return supportedJar;
  }

  public List<AvailableFederation> availableFederations() {
    return availableFederations;
  }

  public List<Map<String, Object>> availableFederationsAsMapList() {
    if (availableFederations == null) {
      return List.of();
    }
    return availableFederations.stream().map(AvailableFederation::toMap).toList();
  }

  public boolean hasAvailableFederations() {
    return availableFederations != null && !availableFederations.isEmpty();
  }

  public AuthenticationInteractionType defaultCibaAuthenticationInteractionType() {
    return new AuthenticationInteractionType(defaultCibaAuthenticationInteractionType);
  }

  public boolean hasDefaultCibaAuthenticationInteractionType() {
    return defaultCibaAuthenticationInteractionType != null
        && !defaultCibaAuthenticationInteractionType.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("access_token_duration", accessTokenDuration);
    map.put("refresh_token_duration", refreshTokenDuration);
    map.put("supported_jar", supportedJar);
    if (hasAvailableFederations())
      map.put("available_federations", availableFederationsAsMapList());
    if (hasDefaultCibaAuthenticationInteractionType())
      map.put(
          "default_ciba_authentication_interaction_type", defaultCibaAuthenticationInteractionType);
    return map;
  }
}

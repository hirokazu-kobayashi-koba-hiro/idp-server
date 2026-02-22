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

package org.idp.server.core.openid.oauth.configuration.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.AuthenticationInteractionType;
import org.idp.server.core.openid.oauth.configuration.RefreshTokenStrategy;
import org.idp.server.platform.json.JsonReadable;

public class ClientExtensionConfiguration implements JsonReadable {

  Long accessTokenDuration;
  Long refreshTokenDuration;
  String refreshTokenStrategy;
  Boolean rotateRefreshToken;
  Long idTokenDuration;
  boolean supportedJar = false;
  List<AvailableFederation> availableFederations;
  String defaultCibaAuthenticationInteractionType = "authentication-device-notification-no-action";
  boolean cibaRequireRar = false;

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

  /** Returns {@code true} if a client-level refresh token strategy override is configured. */
  public boolean hasRefreshTokenStrategy() {
    return refreshTokenStrategy != null && !refreshTokenStrategy.isEmpty();
  }

  /** Returns the client-level refresh token strategy (FIXED or EXTENDS). */
  public RefreshTokenStrategy refreshTokenStrategy() {
    return RefreshTokenStrategy.of(refreshTokenStrategy);
  }

  /** Returns {@code true} if a client-level rotate_refresh_token override is configured. */
  public boolean hasRotateRefreshToken() {
    return rotateRefreshToken != null;
  }

  /** Returns whether refresh tokens should be rotated on use for this client. */
  public boolean isRotateRefreshToken() {
    return rotateRefreshToken;
  }

  /** Returns {@code true} if a client-level id_token_duration override is configured. */
  public boolean hasIdTokenDuration() {
    return idTokenDuration != null && idTokenDuration > 0;
  }

  /** Returns the client-level ID token duration in seconds. */
  public long idTokenDuration() {
    return idTokenDuration;
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

  public boolean isCibaRequireRar() {
    return cibaRequireRar;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (hasAccessTokenDuration()) map.put("access_token_duration", accessTokenDuration);
    if (hasRefreshTokenDuration()) map.put("refresh_token_duration", refreshTokenDuration);
    if (hasRefreshTokenStrategy()) map.put("refresh_token_strategy", refreshTokenStrategy);
    if (hasRotateRefreshToken()) map.put("rotate_refresh_token", rotateRefreshToken);
    if (hasIdTokenDuration()) map.put("id_token_duration", idTokenDuration);
    map.put("supported_jar", supportedJar);
    if (hasAvailableFederations())
      map.put("available_federations", availableFederationsAsMapList());
    if (hasDefaultCibaAuthenticationInteractionType())
      map.put(
          "default_ciba_authentication_interaction_type", defaultCibaAuthenticationInteractionType);
    map.put("ciba_require_rar", cibaRequireRar);
    return map;
  }
}

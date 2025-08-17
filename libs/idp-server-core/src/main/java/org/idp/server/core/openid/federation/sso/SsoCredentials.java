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

package org.idp.server.core.openid.federation.sso;

import java.time.LocalDateTime;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonReadable;

public class SsoCredentials implements JsonReadable {
  String provider;
  String scope;
  String accessToken;
  String refreshToken;
  long accessTokenExpiresIn;
  LocalDateTime accessTokenExpiresAt;
  long refreshTokenExpiresIn;
  LocalDateTime refreshTokenExpiresAt;

  public SsoCredentials() {}

  public SsoCredentials(
      String provider,
      String scope,
      String accessToken,
      String refreshToken,
      long accessTokenExpiresIn,
      LocalDateTime accessTokenExpiresAt,
      long refreshTokenExpiresIn,
      LocalDateTime refreshTokenExpiresAt) {
    this.provider = provider;
    this.scope = scope;
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.accessTokenExpiresIn = accessTokenExpiresIn;
    this.accessTokenExpiresAt = accessTokenExpiresAt;
    this.refreshTokenExpiresIn = refreshTokenExpiresIn;
    this.refreshTokenExpiresAt = refreshTokenExpiresAt;
  }

  public SsoCredentials updateWithToken(
      String accessToken, String refreshToken, long accessTokenExpiresIn) {
    LocalDateTime newAccessTokenExpiresAt = SystemDateTime.now().plusSeconds(accessTokenExpiresIn);
    LocalDateTime newRefreshTokenExpiresAt =
        SystemDateTime.now().plusSeconds(refreshTokenExpiresIn);
    return new SsoCredentials(
        provider,
        scope,
        accessToken,
        refreshToken,
        accessTokenExpiresIn,
        newAccessTokenExpiresAt,
        refreshTokenExpiresIn,
        newRefreshTokenExpiresAt);
  }

  public String provider() {
    return provider;
  }

  public String scope() {
    return scope;
  }

  public String accessToken() {
    return accessToken;
  }

  public String refreshToken() {
    return refreshToken;
  }

  public long accessTokenExpiresIn() {
    return accessTokenExpiresIn;
  }

  public LocalDateTime accessTokenExpiresAt() {
    return accessTokenExpiresAt;
  }

  public long refreshTokenExpiresIn() {
    return refreshTokenExpiresIn;
  }

  public LocalDateTime refreshTokenExpiresAt() {
    return refreshTokenExpiresAt;
  }

  public boolean exists() {
    return provider != null && provider.isEmpty();
  }
}

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

package org.idp.server.core.openid.token;

import java.time.LocalDateTime;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.extension.CreatedAt;
import org.idp.server.core.openid.oauth.type.extension.ExpiresAt;
import org.idp.server.core.openid.oauth.type.oauth.RefreshTokenEntity;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.random.RandomStringGenerator;

/**
 * Provides refresh token creation and refresh logic.
 *
 * <p>Both {@code refresh_token_strategy} and {@code rotate_refresh_token} support client-level
 * overrides. When set on the client, the client value takes precedence; otherwise the tenant
 * (AuthorizationServerConfiguration) value is used.
 *
 * <h3>Refresh behaviour matrix</h3>
 *
 * <table border="1">
 *   <tr><th>Strategy</th><th>Rotate</th><th>Token value</th><th>Expiration</th></tr>
 *   <tr><td>EXTENDS</td><td>true</td><td>New</td><td>New (from now)</td></tr>
 *   <tr><td>EXTENDS</td><td>false</td><td>Old</td><td>New (from now)</td></tr>
 *   <tr><td>FIXED</td><td>true</td><td>New</td><td>Old (unchanged)</td></tr>
 *   <tr><td>FIXED</td><td>false</td><td>Old</td><td>Old (unchanged)</td></tr>
 * </table>
 *
 * @see org.idp.server.core.openid.oauth.configuration.RefreshTokenStrategy
 */
public interface RefreshTokenCreatable {

  /** Creates a brand-new refresh token with a new value and a new expiration. */
  default RefreshToken createRefreshToken(
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    RandomStringGenerator randomStringGenerator = new RandomStringGenerator(32);
    String code = randomStringGenerator.generate();
    RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity(code);

    LocalDateTime localDateTime = SystemDateTime.now();
    CreatedAt createdAt = new CreatedAt(localDateTime);

    long refreshTokenDuration =
        clientConfiguration.hasRefreshTokenDuration()
            ? clientConfiguration.refreshTokenDuration()
            : authorizationServerConfiguration.refreshTokenDuration();

    ExpiresAt expiresAt = new ExpiresAt(localDateTime.plusSeconds(refreshTokenDuration));

    return new RefreshToken(refreshTokenEntity, createdAt, expiresAt);
  }

  /**
   * Refreshes a refresh token according to the resolved strategy and rotation settings.
   *
   * <p>Resolution order for each setting: client override → tenant default.
   *
   * @param oldRefreshToken the existing refresh token to refresh
   * @param authorizationServerConfiguration tenant-level configuration (fallback)
   * @param clientConfiguration client-level configuration (takes precedence when set)
   * @return the refreshed token — may be a new instance, a partially updated instance, or the same
   *     instance depending on the strategy/rotation combination
   */
  default RefreshToken refresh(
      RefreshToken oldRefreshToken,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    boolean isExtends =
        clientConfiguration.hasRefreshTokenStrategy()
            ? clientConfiguration.isExtendsRefreshTokenStrategy()
            : authorizationServerConfiguration.isExtendsRefreshTokenStrategy();

    boolean isRotate =
        clientConfiguration.hasRotateRefreshToken()
            ? clientConfiguration.isRotateRefreshToken()
            : authorizationServerConfiguration.isRotateRefreshToken();

    if (isExtends && isRotate) {
      return createRefreshToken(authorizationServerConfiguration, clientConfiguration);
    }

    if (isExtends) {
      LocalDateTime localDateTime = SystemDateTime.now();
      CreatedAt createdAt = new CreatedAt(localDateTime);

      long refreshTokenDuration =
          clientConfiguration.hasRefreshTokenDuration()
              ? clientConfiguration.refreshTokenDuration()
              : authorizationServerConfiguration.refreshTokenDuration();

      ExpiresAt expiresAt = new ExpiresAt(localDateTime.plusSeconds(refreshTokenDuration));
      RefreshTokenEntity refreshTokenEntity = oldRefreshToken.refreshTokenEntity();

      return new RefreshToken(refreshTokenEntity, createdAt, expiresAt);
    }

    if (isRotate) {
      RandomStringGenerator randomStringGenerator = new RandomStringGenerator(32);
      String code = randomStringGenerator.generate();
      RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity(code);

      LocalDateTime localDateTime = SystemDateTime.now();
      CreatedAt createdAt = new CreatedAt(localDateTime);
      ExpiresAt expiresAt = oldRefreshToken.expiresAt();

      return new RefreshToken(refreshTokenEntity, createdAt, expiresAt);
    }

    return oldRefreshToken;
  }
}

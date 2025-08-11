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

public interface RefreshTokenCreatable {

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

  default RefreshToken refresh(
      RefreshToken oldRefreshToken,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    if (authorizationServerConfiguration.isExtendsAccessTokenStrategy()
        && authorizationServerConfiguration.isRotateRefreshToken()) {

      return createRefreshToken(authorizationServerConfiguration, clientConfiguration);
    }

    if (authorizationServerConfiguration.isExtendsAccessTokenStrategy()) {

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

    RandomStringGenerator randomStringGenerator = new RandomStringGenerator(32);
    String code = randomStringGenerator.generate();
    RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity(code);

    LocalDateTime localDateTime = SystemDateTime.now();
    CreatedAt createdAt = new CreatedAt(localDateTime);
    ExpiresAt expiresAt = oldRefreshToken.expiresAt();

    return new RefreshToken(refreshTokenEntity, createdAt, expiresAt);
  }
}

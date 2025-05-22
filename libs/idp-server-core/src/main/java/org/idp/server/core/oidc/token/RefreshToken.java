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


package org.idp.server.core.oidc.token;

import java.time.LocalDateTime;
import java.util.Objects;
import org.idp.server.basic.type.extension.CreatedAt;
import org.idp.server.basic.type.extension.ExpiredAt;
import org.idp.server.basic.type.oauth.RefreshTokenEntity;

public class RefreshToken {
  RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity();
  CreatedAt createdAt;
  ExpiredAt expiredAt;

  public RefreshToken() {}

  public RefreshToken(
      RefreshTokenEntity refreshTokenEntity, CreatedAt createdAt, ExpiredAt expiredAt) {
    this.refreshTokenEntity = refreshTokenEntity;
    this.createdAt = createdAt;
    this.expiredAt = expiredAt;
  }

  public RefreshTokenEntity refreshTokenEntity() {
    return refreshTokenEntity;
  }

  public CreatedAt createdAt() {
    return createdAt;
  }

  public ExpiredAt expiredAt() {
    return expiredAt;
  }

  public boolean isExpired(LocalDateTime other) {
    return expiredAt.isExpire(other);
  }

  public boolean exists() {
    return Objects.nonNull(refreshTokenEntity) && refreshTokenEntity.exists();
  }
}

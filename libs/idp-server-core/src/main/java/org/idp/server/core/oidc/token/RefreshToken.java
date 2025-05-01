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

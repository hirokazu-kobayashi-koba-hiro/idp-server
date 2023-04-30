package org.idp.server.oauth.token;

import java.time.LocalDateTime;
import java.util.Objects;
import org.idp.server.type.oauth.CreatedAt;
import org.idp.server.type.oauth.ExpiredAt;
import org.idp.server.type.oauth.RefreshTokenValue;

public class RefreshToken {
  RefreshTokenValue refreshTokenValue;
  CreatedAt createdAt;
  ExpiredAt expiredAt;

  public RefreshToken() {}

  public RefreshToken(
      RefreshTokenValue refreshTokenValue, CreatedAt createdAt, ExpiredAt expiredAt) {
    this.refreshTokenValue = refreshTokenValue;
    this.createdAt = createdAt;
    this.expiredAt = expiredAt;
  }

  public RefreshTokenValue refreshTokenValue() {
    return refreshTokenValue;
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
    return Objects.nonNull(refreshTokenValue) && refreshTokenValue.exists();
  }
}

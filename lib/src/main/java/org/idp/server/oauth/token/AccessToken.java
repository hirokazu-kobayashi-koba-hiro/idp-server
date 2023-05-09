package org.idp.server.oauth.token;

import java.time.LocalDateTime;
import java.util.Objects;
import org.idp.server.type.extension.CreatedAt;
import org.idp.server.type.extension.ExpiredAt;
import org.idp.server.type.oauth.AccessTokenValue;

public class AccessToken {
  AccessTokenValue accessTokenValue;
  AccessTokenPayload accessTokenPayload;
  CreatedAt createdAt;
  ExpiredAt expiredAt;

  public AccessToken() {}

  public AccessToken(
      AccessTokenValue accessTokenValue,
      AccessTokenPayload accessTokenPayload,
      CreatedAt createdAt,
      ExpiredAt expiredAt) {
    this.accessTokenValue = accessTokenValue;
    this.accessTokenPayload = accessTokenPayload;
    this.createdAt = createdAt;
    this.expiredAt = expiredAt;
  }

  public AccessTokenValue accessTokenValue() {
    return accessTokenValue;
  }

  public AccessTokenPayload accessTokenPayload() {
    return accessTokenPayload;
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
    return Objects.nonNull(accessTokenValue) && accessTokenValue.exists();
  }
}

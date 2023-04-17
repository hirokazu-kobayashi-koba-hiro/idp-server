package org.idp.server.token;

import java.time.LocalDateTime;
import java.util.Objects;
import org.idp.server.type.oauth.AccessToken;
import org.idp.server.type.oauth.RefreshToken;
import org.idp.server.type.oauth.TokenIssuer;

public class OAuthToken {
  OAuthTokenIdentifier identifier;
  TokenResponse tokenResponse;
  AccessTokenPayload accessTokenPayload;

  public OAuthToken() {}

  public OAuthToken(
      OAuthTokenIdentifier identifier,
      TokenResponse tokenResponse,
      AccessTokenPayload accessTokenPayload) {
    this.identifier = identifier;
    this.tokenResponse = tokenResponse;
    this.accessTokenPayload = accessTokenPayload;
  }

  public OAuthTokenIdentifier identifier() {
    return identifier;
  }

  public TokenIssuer tokenIssuer() {
    return accessTokenPayload.tokenIssuer();
  }

  public TokenResponse tokenResponse() {
    return tokenResponse;
  }

  public AccessTokenPayload accessTokenPayload() {
    return accessTokenPayload;
  }

  public boolean exists() {
    return Objects.nonNull(identifier) && identifier.exists();
  }

  public boolean isExpire(LocalDateTime other) {
    return accessTokenPayload.expiredAt().isExpire(other);
  }

  public AccessToken accessToken() {
    return tokenResponse.accessToken();
  }

  public RefreshToken refreshToken() {
    return tokenResponse.refreshToken();
  }
}

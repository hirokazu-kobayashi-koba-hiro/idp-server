package org.idp.server.token;

import java.time.LocalDateTime;
import java.util.Objects;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.token.AccessTokenPayload;
import org.idp.server.type.oauth.AccessToken;
import org.idp.server.type.oauth.RefreshToken;
import org.idp.server.type.oauth.Subject;
import org.idp.server.type.oauth.TokenIssuer;

public class OAuthToken {
  OAuthTokenIdentifier identifier;
  TokenResponse tokenResponse;
  AccessTokenPayload accessTokenPayload;
  AuthorizationGrant authorizationGrant;

  public OAuthToken() {}

  public OAuthToken(
      OAuthTokenIdentifier identifier,
      TokenResponse tokenResponse,
      AccessTokenPayload accessTokenPayload,
      AuthorizationGrant authorizationGrant) {
    this.identifier = identifier;
    this.tokenResponse = tokenResponse;
    this.accessTokenPayload = accessTokenPayload;
    this.authorizationGrant = authorizationGrant;
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

  public AuthorizationGrant authorizationGrant() {
    return authorizationGrant;
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

  public boolean hasRefreshToken() {
    return tokenResponse.hasRefreshToken();
  }

  public Subject subject() {
    return accessTokenPayload.subject();
  }

  public boolean hasOpenidScope() {
    return accessTokenPayload.hasOpenidScope();
  }
}

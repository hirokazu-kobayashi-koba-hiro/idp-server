package org.idp.server.oauth.token;

import java.time.LocalDateTime;
import java.util.Objects;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.type.extension.CreatedAt;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.extension.ExpiredAt;
import org.idp.server.type.oauth.*;

public class AccessToken {
  TokenIssuer tokenIssuer;
  TokenType tokenType;
  AccessTokenValue accessTokenValue;
  AuthorizationGrant authorizationGrant;
  CreatedAt createdAt;
  ExpiresIn expiresIn;
  ExpiredAt expiredAt;

  public AccessToken() {}

  public AccessToken(
      TokenIssuer tokenIssuer,
      TokenType tokenType,
      AccessTokenValue accessTokenValue,
      AuthorizationGrant authorizationGrant,
      CreatedAt createdAt,
      ExpiresIn expiresIn,
      ExpiredAt expiredAt) {
    this.tokenIssuer = tokenIssuer;
    this.tokenType = tokenType;
    this.accessTokenValue = accessTokenValue;
    this.authorizationGrant = authorizationGrant;
    this.createdAt = createdAt;
    this.expiresIn = expiresIn;
    this.expiredAt = expiredAt;
  }

  public TokenIssuer tokenIssuer() {
    return tokenIssuer;
  }

  public TokenType tokenType() {
    return tokenType;
  }

  public AccessTokenValue accessTokenValue() {
    return accessTokenValue;
  }

  public AuthorizationGrant authorizationGrant() {
    return authorizationGrant;
  }

  public boolean hasSubject() {
    return authorizationGrant.hasUser();
  }

  public Subject subject() {
    return authorizationGrant.subject();
  }

  public CreatedAt createdAt() {
    return createdAt;
  }

  public ExpiresIn expiresIn() {
    return expiresIn;
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

  public ClientId clientId() {
    return authorizationGrant.clientId();
  }

  public Scopes scopes() {
    return authorizationGrant.scopes();
  }

  public boolean hasCustomProperties() {
    return authorizationGrant.hasCustomProperties();
  }

  public CustomProperties customProperties() {
    return authorizationGrant.customProperties();
  }
}

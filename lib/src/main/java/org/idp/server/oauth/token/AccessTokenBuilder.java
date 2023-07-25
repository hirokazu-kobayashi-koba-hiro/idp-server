package org.idp.server.oauth.token;

import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.mtls.ClientCertificationThumbprint;
import org.idp.server.type.extension.CreatedAt;
import org.idp.server.type.extension.ExpiredAt;
import org.idp.server.type.oauth.AccessTokenEntity;
import org.idp.server.type.oauth.ExpiresIn;
import org.idp.server.type.oauth.TokenIssuer;
import org.idp.server.type.oauth.TokenType;

public class AccessTokenBuilder {
  TokenIssuer tokenIssuer;
  TokenType tokenType;
  AccessTokenEntity accessTokenEntity;
  AuthorizationGrant authorizationGrant;
  ClientCertificationThumbprint clientCertificationThumbprint;
  CreatedAt createdAt;
  ExpiresIn expiresIn;
  ExpiredAt expiredAt;

  public AccessTokenBuilder(TokenIssuer tokenIssuer) {
    this.tokenIssuer = tokenIssuer;
  }

  public AccessTokenBuilder add(TokenType tokenType) {
    this.tokenType = tokenType;
    return this;
  }

  public AccessTokenBuilder add(AccessTokenEntity accessTokenEntity) {
    this.accessTokenEntity = accessTokenEntity;
    return this;
  }

  public AccessTokenBuilder add(AuthorizationGrant authorizationGrant) {
    this.authorizationGrant = authorizationGrant;
    return this;
  }

  public AccessTokenBuilder add(ClientCertificationThumbprint clientCertificationThumbprint) {
    this.clientCertificationThumbprint = clientCertificationThumbprint;
    return this;
  }

  public AccessTokenBuilder add(CreatedAt createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public AccessTokenBuilder add(ExpiresIn expiresIn) {
    this.expiresIn = expiresIn;
    return this;
  }

  public AccessTokenBuilder add(ExpiredAt expiredAt) {
    this.expiredAt = expiredAt;
    return this;
  }

  public AccessToken build() {
    return new AccessToken(
        tokenIssuer,
        tokenType,
            accessTokenEntity,
        authorizationGrant,
        clientCertificationThumbprint,
        createdAt,
        expiresIn,
        expiredAt);
  }
}

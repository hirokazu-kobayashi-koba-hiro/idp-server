package org.idp.server.core.oauth.token;

import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.mtls.ClientCertificationThumbprint;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.extension.CreatedAt;
import org.idp.server.core.type.extension.ExpiredAt;
import org.idp.server.core.type.oauth.AccessTokenEntity;
import org.idp.server.core.type.oauth.ExpiresIn;
import org.idp.server.core.type.oauth.TokenIssuer;
import org.idp.server.core.type.oauth.TokenType;

public class AccessTokenBuilder {
  TenantIdentifier tenantIdentifier;
  TokenIssuer tokenIssuer;
  TokenType tokenType;
  AccessTokenEntity accessTokenEntity;
  AuthorizationGrant authorizationGrant;
  ClientCertificationThumbprint clientCertificationThumbprint;
  CreatedAt createdAt;
  ExpiresIn expiresIn;
  ExpiredAt expiredAt;

  public AccessTokenBuilder(TenantIdentifier tenantIdentifier) {
    this.tenantIdentifier = tenantIdentifier;
  }

  public AccessTokenBuilder add(TokenIssuer tokenIssuer) {
    this.tokenIssuer = tokenIssuer;
    return this;
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
        tenantIdentifier,
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

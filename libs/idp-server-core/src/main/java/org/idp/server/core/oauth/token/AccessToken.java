package org.idp.server.core.oauth.token;

import java.time.LocalDateTime;
import java.util.Objects;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.mtls.ClientCertificationThumbprint;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.type.extension.CreatedAt;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.extension.ExpiredAt;
import org.idp.server.core.type.oauth.*;

public class AccessToken {
  TokenIssuer tokenIssuer;
  TokenType tokenType;
  AccessTokenEntity accessTokenEntity;
  AuthorizationGrant authorizationGrant;
  ClientCertificationThumbprint clientCertificationThumbprint;
  CreatedAt createdAt;
  ExpiresIn expiresIn;
  ExpiredAt expiredAt;

  public AccessToken() {}

  public AccessToken(
      TokenIssuer tokenIssuer,
      TokenType tokenType,
      AccessTokenEntity accessTokenEntity,
      AuthorizationGrant authorizationGrant,
      ClientCertificationThumbprint clientCertificationThumbprint,
      CreatedAt createdAt,
      ExpiresIn expiresIn,
      ExpiredAt expiredAt) {
    this.tokenIssuer = tokenIssuer;
    this.tokenType = tokenType;
    this.accessTokenEntity = accessTokenEntity;
    this.authorizationGrant = authorizationGrant;
    this.clientCertificationThumbprint = clientCertificationThumbprint;
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

  public AccessTokenEntity accessTokenValue() {
    return accessTokenEntity;
  }

  public AuthorizationGrant authorizationGrant() {
    return authorizationGrant;
  }

  public ClientCertificationThumbprint clientCertificationThumbprint() {
    return clientCertificationThumbprint;
  }

  public boolean hasClientCertification() {
    return clientCertificationThumbprint.exists();
  }

  public boolean isSenderConstrained() {
    return clientCertificationThumbprint.exists();
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
    return Objects.nonNull(accessTokenEntity) && accessTokenEntity.exists();
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

  public boolean hasAuthorizationDetails() {
    return authorizationGrant.hasAuthorizationDetails();
  }

  public AuthorizationDetails authorizationDetails() {
    return authorizationGrant.authorizationDetails();
  }

  public boolean matchThumbprint(ClientCertificationThumbprint thumbprint) {
    return clientCertificationThumbprint.equals(thumbprint);
  }
}

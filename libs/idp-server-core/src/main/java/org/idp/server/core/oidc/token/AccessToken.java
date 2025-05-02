package org.idp.server.core.oidc.token;

import java.time.LocalDateTime;
import java.util.Objects;
import org.idp.server.basic.type.extension.CreatedAt;
import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.basic.type.extension.ExpiredAt;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.oidc.client.Client;
import org.idp.server.core.oidc.client.ClientIdentifier;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.mtls.ClientCertificationThumbprint;
import org.idp.server.core.oidc.rar.AuthorizationDetails;

public class AccessToken {
  TenantIdentifier tenantIdentifier;
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
      TenantIdentifier tenantIdentifier,
      TokenIssuer tokenIssuer,
      TokenType tokenType,
      AccessTokenEntity accessTokenEntity,
      AuthorizationGrant authorizationGrant,
      ClientCertificationThumbprint clientCertificationThumbprint,
      CreatedAt createdAt,
      ExpiresIn expiresIn,
      ExpiredAt expiredAt) {
    this.tenantIdentifier = tenantIdentifier;
    this.tokenIssuer = tokenIssuer;
    this.tokenType = tokenType;
    this.accessTokenEntity = accessTokenEntity;
    this.authorizationGrant = authorizationGrant;
    this.clientCertificationThumbprint = clientCertificationThumbprint;
    this.createdAt = createdAt;
    this.expiresIn = expiresIn;
    this.expiredAt = expiredAt;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenantIdentifier;
  }

  public TokenIssuer tokenIssuer() {
    return tokenIssuer;
  }

  public TokenType tokenType() {
    return tokenType;
  }

  public AccessTokenEntity accessTokenEntity() {
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

  public ClientIdentifier clientIdentifier() {
    return authorizationGrant.clientIdentifier();
  }

  public RequestedClientId requestedClientId() {
    return authorizationGrant.requestedClientId();
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

  public User user() {
    return authorizationGrant.user();
  }

  public Client client() {
    return authorizationGrant.client();
  }
}
